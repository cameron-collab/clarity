import os
import json
from datetime import datetime, timezone, timedelta, date
from typing import Optional, Dict, Any, Tuple
import re
import hashlib
import base64

from fastapi import FastAPI, Request, Header, HTTPException
from fastapi.responses import JSONResponse, PlainTextResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from dotenv import load_dotenv

import stripe
from twilio.request_validator import RequestValidator
from twilio.rest import Client as TwilioClient

import snowflake.connector as sf
from cryptography.hazmat.primitives import serialization

# ---------- Env & third-party setup ----------
load_dotenv()

stripe.api_key = os.getenv("STRIPE_SECRET_KEY", "")

TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID", "")
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "")
TWILIO_MESSAGING_SERVICE_SID = os.getenv("TWILIO_MESSAGING_SERVICE_SID", "")
TWILIO_FROM_NUMBER = os.getenv("TWILIO_FROM_NUMBER", "")

twilio_validator = RequestValidator(TWILIO_AUTH_TOKEN) if TWILIO_AUTH_TOKEN else None
twilio_client = (TwilioClient(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
                 if (TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN) else None)

SNOW_USER = os.environ["SNOW_USER"]
SNOW_ACCOUNT = os.environ["SNOW_ACCOUNT"]  # e.g., su53882.canada-central.azure
SNOW_WAREHOUSE = os.environ.get("SNOW_WAREHOUSE", "APP_WH")
SNOW_ROLE = os.environ.get("SNOW_ROLE", "APP_WRITER")
SNOW_DATABASE = os.environ.get("SNOW_DATABASE", "PHOENIX_APP_DEV")
SNOW_SCHEMA = os.environ.get("SNOW_SCHEMA", "CORE")
KEY_PATH = os.environ["SNOW_PRIVATE_KEY_PATH"]
PASSPHRASE = os.environ.get("SNOW_PRIVATE_KEY_PASSPHRASE")  # may be None

# New internal stage just for signatures (no @ here)
SIGNATURE_STAGE_NAME = "PHOENIX_APP_DEV.CORE.ASSETS_INT"
SIGNATURE_STAGE_URI_PREFIX = f"@{SIGNATURE_STAGE_NAME}"  # -> "@PHOENIX_APP_DEV.CORE.ASSETS_INT"



def get_snowflake_ctx():
    # Load private key (DER PKCS8)
    with open(KEY_PATH, "rb") as f:
        pk = serialization.load_pem_private_key(
            f.read(),
            password=PASSPHRASE.encode("utf-8") if PASSPHRASE else None,
        )
    private_key_der = pk.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return sf.connect(
        user=SNOW_USER,
        account=SNOW_ACCOUNT,
        private_key=private_key_der,
        warehouse=SNOW_WAREHOUSE,
        role=SNOW_ROLE,
        database=SNOW_DATABASE,
        schema=SNOW_SCHEMA,
    )

# ---------- App ----------
app = FastAPI(title="Globalfaces Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten later
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------- Models ----------
class LogEventIn(BaseModel):
    event_type: str = Field(..., examples=["CONNECTOR_BOOT", "SESSION_STARTED"])
    session_id: Optional[str] = None
    donor_id: Optional[str] = None
    fundraiser_id: Optional[str] = None
    attributes: Dict[str, Any] = Field(default_factory=dict)

class PaymentIntentIn(BaseModel):
    amount: int = Field(..., description="Amount in cents, e.g., 2000 for $20.00")
    currency: str = Field(default="cad")
    session_id: Optional[str] = None
    donor_id: Optional[str] = None

class SetupIntentIn(BaseModel):
    customer_id: str
    usage: str = Field(default="off_session")
    session_id: Optional[str] = None
    donor_id: Optional[str] = None

class SubscriptionCreateIn(BaseModel):
    customer_id: str
    price_id: str
    cancel_after_years: int = 50
    metadata: Dict[str, Any] = Field(default_factory=dict)
    session_id: Optional[str] = None
    donor_id: Optional[str] = None

class CustomerUpsertIn(BaseModel):
    email: str
    name: str
    phone: Optional[str] = None
    metadata: Dict[str, Any] = Field(default_factory=dict)

class PaymentMethodAttachIn(BaseModel):
    customer_id: str
    payment_method_id: str
    session_id: Optional[str] = None
    donor_id: Optional[str] = None
    save_row: bool = True

class SendSmsIn(BaseModel):
    to_e164: str
    session_id: str
    donor_id: str
    charity_name: str
    gift_type: str = "MONTHLY"       # 'MONTHLY' | 'OTG'
    amount_cents: int                # 2000 => $20.00
    currency: str = "CAD"
    preview_message: Optional[str] = None

class FundraiserLoginIn(BaseModel):
    fundraiser_id: str

class FundraiserLoginOut(BaseModel):
    session_id: str
    fundraiser: Dict[str, Any]
    charity: Dict[str, Any] | None = None
    campaign: Dict[str, Any] | None = None

class DonorUpsertIn(BaseModel):
    donor_id: str | None = None
    title: str | None = None
    first_name: str
    middle_name: str | None = None
    last_name: str
    dob_iso: str
    mobile_e164: str
    email: str
    address1: str
    address2: str | None = None
    city: str
    region: str
    postal_code: str
    country: str = "CA"
    fundraiser_id: str
    session_id: str
    
class DonorConsentIn(BaseModel):
    session_id: str
    donor_id: str
    consent_sms: bool = True
    consent_email: bool = True
    consent_mail: bool = True
    
class TerminalPaymentIntentIn(BaseModel):
    amount: int
    currency: str = "cad"
    session_id: str | None = None
    donor_id: str | None = None
    location_id: str | None = None  # Stripe Terminal Location (recommended)
    
class SignatureUploadIn(BaseModel):
    session_id: str
    donor_id: str
    signature_data: str  # Base64 encoded PNG data

class SignatureUploadOut(BaseModel):
    signature_id: str
    signature_url: str
    success: bool
    
# ---------- Helpers ----------
def _json_default(o):
    if isinstance(o, (date, datetime)):
        return o.isoformat()
    return str(o)

def years_from_now_utc(years: int) -> int:
    dt = datetime.now(timezone.utc)
    try:
        dt = dt.replace(year=dt.year + years)
    except ValueError:
        dt = dt + timedelta(days=365 * years)
    return int(dt.timestamp())

def insert_event(cur, ev: LogEventIn, event_id: Optional[str] = None):
    eid = event_id or f"evt-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')}"
    cur.execute(
        """
        INSERT INTO EVENT_LOG (EVENT_ID, SESSION_ID, DONOR_ID, FUNDRAISER_ID, EVENT_TYPE, ATTRIBUTES)
        SELECT %s, %s, %s, %s, %s, PARSE_JSON(%s)
        """,
        (eid, ev.session_id, ev.donor_id, ev.fundraiser_id, ev.event_type,
         json.dumps(ev.attributes, default=_json_default)),
    )
    return eid

def row_to_dict(cur, row, cols=None):
    if row is None:
        return None
    if cols is None:
        cols = [d[0] for d in cur.description]
    return {c: v for c, v in zip(cols, row)}

def _nz(x): return (x or "").strip()

def presign_stage_url(cur, stage_uri: str, expires_sec: int = 3600) -> str | None:
    """
    Accepts a Snowflake stage URI like:
      @DB.SCHEMA.STAGE/path/to/file.png
    Returns a presigned HTTPS URL via GET_PRESIGNED_URL. Works for internal and external stages.
    """
    if not stage_uri or not stage_uri.strip().startswith("@"):
        return None

    m = re.match(r"^@(?P<stage>[A-Za-z0-9_\.]+)/(.*)$", stage_uri.strip())
    if not m:
        return None

    stage_name = m.group("stage")  # e.g., "PHOENIX_APP_DEV.CORE.ASSETS" or "...ASSETS_INT"
    path = stage_uri.strip()[len(f"@{stage_name}/"):]  # e.g., "logos/CH003.png" or "signatures/xxx.png"

    try:
        row = cur.execute(
            f"SELECT GET_PRESIGNED_URL(@{stage_name}, %s, %s)",
            (path, expires_sec)
        ).fetchone()
        return row[0] if row and row[0] else None
    except Exception as e:
        print(f"=== DEBUG: GET_PRESIGNED_URL failed for {stage_name}/{path}: {e} ===")
        return None

    
 

# ---------- Routes ----------
@app.get("/healthz")
def healthz():
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        who = cur.execute(
            "SELECT CURRENT_USER(), CURRENT_ROLE(), CURRENT_WAREHOUSE(), CURRENT_DATABASE(), CURRENT_SCHEMA()"
        ).fetchone()
        return {
            "ok": True,
            "snowflake": {
                "user": who[0],
                "role": who[1],
                "wh": who[2],
                "db": who[3],
                "schema": who[4],
            },
        }
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

@app.post("/log-event")
def log_event(ev: LogEventIn):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        event_id = insert_event(cur, ev)
        return {"ok": True, "event_id": event_id}
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- SMS: outbound send (FINAL MESSAGE FORMAT) ----------
@app.post("/verification/sms/send")
def send_verification_sms(payload: SendSmsIn, request: Request):
    if not twilio_client:
        raise HTTPException(status_code=500, detail="Twilio client not configured")

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()

        # --- Donor fields for message ---
        cur.execute(
            """
            SELECT TITLE, FIRST_NAME, MIDDLE_NAME, LAST_NAME,
                   EMAIL, ADDRESS1, ADDRESS2, CITY, REGION, POSTAL_CODE, COUNTRY,
                   DOB_DATE
            FROM DONOR
            WHERE DONOR_ID = %s
            """,
            (payload.donor_id,)
        )
        drow = cur.fetchone()
        if not drow:
            raise HTTPException(status_code=404, detail="Donor not found")

        (title, first, middle, last,
         email, addr1, addr2, city, region, postal, country,
         dob_date) = drow

        # --- Fundraiser first name from session ---
        cur.execute(
            """
            SELECT F.DISPLAY_NAME
            FROM SESSION S
            JOIN FUNDRAISER F ON F.FUNDRAISER_ID = S.FUNDRAISER_ID
            WHERE S.SESSION_ID = %s
            """,
            (payload.session_id,)
        )
        frow = cur.fetchone()
        fundraiser_display = (frow[0] if frow else "") or ""
        fundraiser_first = (fundraiser_display.strip().split(" ")[0]) if fundraiser_display else "your fundraiser"

        # Donor full name: Title + First + Middle + Last (non-blank)
        name_parts = [_nz(title), _nz(first), _nz(middle), _nz(last)]
        donor_full_name = " ".join([p for p in name_parts if p])

        # Address line (skip address2 if blank)
        addr_parts = [_nz(addr1)]
        if _nz(addr2):
            addr_parts.append(_nz(addr2))
        addr_parts += [_nz(city), _nz(region), _nz(postal), _nz(country)]
        address_line = ", ".join([p for p in addr_parts if p])

        # DOB as ISO
        dob_iso = dob_date.isoformat() if hasattr(dob_date, "isoformat") else _nz(dob_date)

        # Amount + frequency + charity
        amount_str = f"${payload.amount_cents/100:.2f} {payload.currency.upper()}"
        freq_txt = "(monthly)" if (payload.gift_type or "").upper() == "MONTHLY" else "(one-time)"
        charity_txt = _nz(payload.charity_name)

        # FINAL BODY — EXACT WORDING
        desired_body = (
            f"Hi {donor_full_name}! It's {fundraiser_first}.  "
            f"Thank you for committing to donate {amount_str} {freq_txt} to {charity_txt}.\n\n"
            f"Your information is as follows:\n"
            f"Email address: {email}\n"
            f"Address: {address_line}\n"
            f"Date of Birth: {dob_iso}\n\n"
            f"Please confirm (yes/no) that everything above is correct."
        )
        body = payload.preview_message or desired_body

        # --- Send SMS via Twilio ---
        msg_kwargs = {"to": payload.to_e164, "body": body}
        if TWILIO_MESSAGING_SERVICE_SID:
            msg_kwargs["messaging_service_sid"] = TWILIO_MESSAGING_SERVICE_SID
        elif TWILIO_FROM_NUMBER:
            msg_kwargs["from_"] = TWILIO_FROM_NUMBER
        else:
            raise HTTPException(status_code=500, detail="Set TWILIO_MESSAGING_SERVICE_SID or TWILIO_FROM_NUMBER")

        msg = twilio_client.messages.create(**msg_kwargs)

        # Persist tracking row
        cur.execute(
            """
            INSERT INTO VERIFICATION_SMS
            (VERIF_ID, SESSION_ID, DONOR_ID, SENT_TS, MESSAGE_BODY,
             INBOUND_TS, INBOUND_BODY, RESULT, TWILIO_MSG_SID, MOBILE_E164, TO_NUMBER)
            SELECT %s, %s, %s, CURRENT_TIMESTAMP(), %s,
                   NULL, NULL, NULL, %s, %s, %s
            """,
            (
                f"tw-{msg.sid}",
                payload.session_id,
                payload.donor_id,
                body,
                msg.sid,
                payload.to_e164,
                getattr(msg, "from_", None),
            ),
        )
        insert_event(
            cur,
            LogEventIn(
                event_type="SMS_SENT",
                session_id=payload.session_id,
                donor_id=payload.donor_id,
                attributes={
                    "to": payload.to_e164,
                    "sid": msg.sid,
                    "body": body,
                    "fundraiser_first": fundraiser_first,
                    "donor_full_name": donor_full_name,
                },
            ),
        )

    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    return {"ok": True, "sid": msg.sid}

# ---------- SMS VERIFICATION ROUTE ----------
@app.get("/verification/sms/status")
def verification_status(session_id: Optional[str] = None, donor_id: Optional[str] = None):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        cur.execute(
            """
            SELECT RESULT, INBOUND_BODY, SENT_TS
            FROM VERIFICATION_SMS
            WHERE SESSION_ID = %s AND DONOR_ID = %s
            ORDER BY SENT_TS DESC
            LIMIT 1
            """,
            (session_id, donor_id),
        )
        row = cur.fetchone()
        if not row:
            return {"result": "PENDING", "inbound_body": None}
        result, inbound_body, sent_ts = row
        return {"result": result or "PENDING", "inbound_body": inbound_body, "sent_ts": sent_ts.isoformat() if sent_ts else None}
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# --- Stripe Terminal: connection token ---
@app.post("/terminal/connection_token")
def terminal_connection_token():
    try:
        # requires: STRIPE_SECRET_KEY + STRIPE_TERMINAL_LOCATION_ID
        loc = os.getenv("STRIPE_TERMINAL_LOCATION_ID", None)
        # If you want to scope tokens to a location (recommended)
        ct = stripe.terminal.ConnectionToken.create(
            location=loc if loc else None
        )
        return {"secret": ct.secret}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Terminal token error: {e}")

        
# --- Stripe Terminal: payment_intent ---
@app.post("/terminal/payment_intent")
def create_terminal_payment_intent(p: TerminalPaymentIntentIn):
    try:
        pm_types = ["card_present"]
        if (p.currency or "").lower() == "cad":
            pm_types.append("interac_present")
            
        # Use exact same structure as working curl command
        kwargs = {
            "amount": p.amount,
            "currency": p.currency,
            "capture_method": "automatic",
            "payment_method_types": pm_types,
            "setup_future_usage": "off_session",  # This should work based on curl test
            "metadata": {
                "session_id": p.session_id or "", 
                "donor_id": p.donor_id or ""
            },
        }
        
        if p.location_id:
            kwargs["payment_method_options"] = {
                "card_present": {"request_extended_authorization": "if_available"}
            }
            
        pi = stripe.PaymentIntent.create(**kwargs)
        return {"id": pi.id, "client_secret": pi.client_secret, "status": pi.status}
    except stripe.error.StripeError as e:
        print(f"Stripe Error Details: {e}")
        raise HTTPException(status_code=400, detail=f"Stripe Error: {e}")
    except Exception as e:
        print(f"General Error: {e}")
        raise HTTPException(status_code=400, detail=f"Terminal PI error: {e}")
        
# ---------- Stripe: Get Payment Method ----------
# Update your backend endpoint to retrieve the generated_card
@app.get("/payment_intent/{payment_intent_id}/payment_method")
def get_payment_method_from_intent(payment_intent_id: str):
    try:
        # Retrieve the PaymentIntent with expanded latest_charge
        pi = stripe.PaymentIntent.retrieve(
            payment_intent_id,
            expand=['latest_charge']
        )
        
        print(f"=== BACKEND DEBUG: PaymentIntent = {pi} ===")
        
        payment_method_id = None
        generated_card_id = None
        
        # First check if there's a direct payment method
        if pi.payment_method:
            payment_method_id = pi.payment_method
        elif pi.charges and pi.charges.data:
            charge = pi.charges.data[0]
            payment_method_id = charge.payment_method
            
        print(f"=== BACKEND DEBUG: payment_method_id = {payment_method_id} ===")
        print(f"=== BACKEND DEBUG: latest_charge = {pi.latest_charge} ===")
        
        # For Terminal payments, check for generated_card
        if pi.latest_charge and pi.latest_charge.payment_method_details:
            payment_details = pi.latest_charge.payment_method_details
            print(f"=== BACKEND DEBUG: payment_method_details = {payment_details} ===")
            if payment_details.card_present:
                print(f"=== BACKEND DEBUG: card_present = {payment_details.card_present} ===")
                generated_card_id = payment_details.card_present.generated_card
                print(f"=== BACKEND DEBUG: generated_card_id = {generated_card_id} ===")
                
        return {
            "payment_method_id": payment_method_id,
            "generated_card_id": generated_card_id,
            "status": pi.status
        }
    except Exception as e:
        print(f"=== BACKEND DEBUG: Exception in get_payment_method_from_intent: {e} ===")
        raise HTTPException(status_code=400, detail=f"Error retrieving payment method: {e}")
        
# ---------- Stripe: PaymentIntent (OTG) ----------
@app.post("/payment_intent")
def create_payment_intent(payload: PaymentIntentIn):
    try:
        idem = f"{payload.session_id}-pi-1" if payload.session_id else None
        kwargs = dict(
            amount=payload.amount,
            currency=payload.currency,
            capture_method="automatic",
            metadata={"session_id": payload.session_id or "", "donor_id": payload.donor_id or ""},
        )
        if idem:
            kwargs["idempotency_key"] = idem
        pi = stripe.PaymentIntent.create(**kwargs)
        return {"client_secret": pi.client_secret, "id": pi.id, "status": pi.status}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

# ---------- Stripe: SetupIntent (save card for monthly) ----------
@app.post("/setup_intent")
def create_setup_intent(payload: SetupIntentIn):
    try:
        si = stripe.SetupIntent.create(
            customer=payload.customer_id,
            usage=payload.usage,
            metadata={"session_id": payload.session_id or "", "donor_id": payload.donor_id or ""},
        )
        return {"client_secret": si.client_secret, "id": si.id, "status": si.status}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

# ---------- Stripe webhook (with de-dupe + metadata enrichment) ----------
def _enrich_session_donor_from_stripe(event: dict) -> Tuple[Optional[str], Optional[str]]:
    obj = event.get("data", {}).get("object", {}) or {}
    md = obj.get("metadata") or {}
    session_id = md.get("session_id")
    donor_id = md.get("donor_id")
    if session_id or donor_id:
        return session_id, donor_id

    def _md(x): return (x or {}).get("metadata") or {}

    pi_id = None
    if obj.get("object") == "payment_intent":
        pi_id = obj.get("id")
    elif isinstance(obj.get("payment_intent"), str):
        pi_id = obj["payment_intent"]
    elif isinstance(obj.get("payment_intent"), dict):
        pi_id = obj["payment_intent"].get("id")

    if pi_id and stripe.api_key:
        try:
            pi = stripe.PaymentIntent.retrieve(pi_id)
            md2 = _md(pi)
            session_id = session_id or md2.get("session_id")
            donor_id   = donor_id   or md2.get("donor_id")
            if session_id or donor_id:
                return session_id, donor_id
        except Exception:
            pass

    inv_id = None
    if obj.get("object") == "invoice":
        inv_id = obj.get("id")
    elif isinstance(obj.get("invoice"), str):
        inv_id = obj["invoice"]

    sub_id_from_invoice = None
    if inv_id and stripe.api_key:
        try:
            inv = stripe.Invoice.retrieve(inv_id)
            md3 = _md(inv)
            session_id = session_id or md3.get("session_id")
            donor_id   = donor_id   or md3.get("donor_id")
            sub_id_from_invoice = inv.get("subscription") if isinstance(inv.get("subscription"), str) else None
            pi_id2 = inv.get("payment_intent") if isinstance(inv.get("payment_intent"), str) else None
            if (not session_id or not donor_id) and pi_id2:
                try:
                    pi2 = stripe.PaymentIntent.retrieve(pi_id2)
                    md_pi2 = _md(pi2)
                    session_id = session_id or md_pi2.get("session_id")
                    donor_id   = donor_id   or md_pi2.get("donor_id")
                except Exception:
                    pass
            if session_id or donor_id:
                return session_id, donor_id
        except Exception:
            pass

    sub_id = None
    if obj.get("object") == "subscription":
        sub_id = obj.get("id")
    elif isinstance(obj.get("subscription"), str):
        sub_id = obj["subscription"]
    if not sub_id:
        sub_id = sub_id_from_invoice

    if sub_id and stripe.api_key:
        try:
            sub = stripe.Subscription.retrieve(sub_id)
            md4 = _md(sub)
            session_id = session_id or md4.get("session_id")
            donor_id   = donor_id   or md4.get("donor_id")
        except Exception:
            pass

    return session_id, donor_id

@app.post("/webhook/stripe")
async def stripe_webhook(
    request: Request,
    stripe_signature: Optional[str] = Header(None, alias="Stripe-Signature"),
):
    webhook_secret = os.getenv("STRIPE_WEBHOOK_SECRET", "")
    payload = await request.body()
    try:
        if webhook_secret:
            event = stripe.Webhook.construct_event(payload=payload, sig_header=stripe_signature, secret=webhook_secret)
        else:
            event = json.loads(payload)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Webhook signature error: {str(e)}")

    etype = event["type"]
    data = event["data"]["object"]
    event_id = event["id"]

    s_id, d_id = _enrich_session_donor_from_stripe(event)

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        exists = cur.execute(
            "SELECT 1 FROM EVENT_LOG WHERE EVENT_ID = %s LIMIT 1", (event_id,)
        ).fetchone()
        if not exists:
            insert_event(
                cur,
                LogEventIn(
                    event_type=f"STRIPE_{etype.upper()}",
                    session_id=s_id,
                    donor_id=d_id,
                    attributes=data,
                ),
                event_id=event_id,
            )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    return JSONResponse({"received": True})

# ---------- Stripe: Subscription (charge automatically; 50y end) ----------
@app.post("/subscriptions/create")
def create_subscription(payload: SubscriptionCreateIn):
    try:
        cancel_at_ts = years_from_now_utc(payload.cancel_after_years)
        sub = stripe.Subscription.create(
            customer=payload.customer_id,
            items=[{"price": payload.price_id}],
            cancel_at=cancel_at_ts,
            collection_method="charge_automatically",
            payment_behavior="default_incomplete",  # Add this line
            expand=["latest_invoice.payment_intent", "latest_invoice.charge"],
            metadata=payload.metadata
            | {"session_id": payload.session_id or "", "donor_id": payload.donor_id or ""},
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Stripe subscription error: {e}")

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        insert_event(
            cur,
            LogEventIn(
                event_type="SUBSCRIPTION_CREATED",
                session_id=payload.session_id,
                donor_id=payload.donor_id,
                attributes={"subscription_id": sub.id, "cancel_at": sub.cancel_at, "price_id": payload.price_id},
            ),
        )
        cur.execute(
            """
            INSERT INTO PAYMENT (PAYMENT_ID, SESSION_ID, DONOR_ID, TYPE, AMOUNT, CURRENCY,
                                 STRIPE_CUSTOMER_ID, STRIPE_SUBSCRIPTION_ID, STATUS, CREATED_AT)
            SELECT %s, %s, %s, 'MONTHLY', NULL, NULL, %s, %s, %s, CURRENT_TIMESTAMP()
            """,
            (
                f"sub-{sub.id}",
                payload.session_id,
                payload.donor_id,
                payload.customer_id,
                sub.id,
                sub.status,
            ),
        )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    return {
        "id": sub.id,
        "status": sub.status,
        "cancel_at": sub.cancel_at,
        "latest_invoice": (sub.latest_invoice.id if getattr(sub, "latest_invoice", None) else None),
        "payment_intent": (
            sub.latest_invoice.payment_intent.id
            if getattr(sub, "latest_invoice", None) and getattr(sub.latest_invoice, "payment_intent", None)
            else None
        ),
    }

# ---------- Stripe: Customer upsert ----------
@app.post("/customer/upsert")
def upsert_customer(payload: CustomerUpsertIn):
    try:
        existing = stripe.Customer.search(query=f"email:'{payload.email}'")
        if existing.data:
            cust = existing.data[0]
        else:
            cust = stripe.Customer.create(
                email=payload.email,
                name=payload.name,
                phone=payload.phone,
                metadata=payload.metadata,
            )
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Stripe customer error: {e}")

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        insert_event(
            cur,
            LogEventIn(
                event_type="CUSTOMER_UPSERT",
                donor_id=payload.metadata.get("donor_id"),
                attributes={"customer_id": cust.id, "email": payload.email},
            ),
        )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    return {"customer_id": cust.id}

# ---------- Stripe: Attach PM & set default ----------
@app.get("/payment_intent/{payment_intent_id}/payment_method")
def get_payment_method_from_intent(payment_intent_id: str):
    try:
        # Retrieve the PaymentIntent with expanded latest_charge
        pi = stripe.PaymentIntent.retrieve(
            payment_intent_id,
            expand=['latest_charge']
        )
        
        payment_method_id = None
        generated_card_id = None
        
        # First check if there's a direct payment method
        if pi.payment_method:
            payment_method_id = pi.payment_method
        elif pi.charges and pi.charges.data:
            charge = pi.charges.data[0]
            payment_method_id = charge.payment_method
            
        # For Terminal payments, check for generated_card
        if pi.latest_charge and pi.latest_charge.payment_method_details:
            payment_details = pi.latest_charge.payment_method_details
            if payment_details.card_present:
                generated_card_id = payment_details.card_present.generated_card
                
        return {
            "payment_method_id": payment_method_id,
            "generated_card_id": generated_card_id,
            "status": pi.status
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Error retrieving payment method: {e}")

    # Success case - existing code
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        insert_event(
            cur,
            LogEventIn(
                event_type="PAYMENT_METHOD_ATTACHED",
                session_id=payload.session_id,
                donor_id=payload.donor_id,
                attributes={
                    "customer_id": payload.customer_id,
                    "payment_method_id": payload.payment_method_id,
                    "default_pm": (cust.invoice_settings.default_payment_method if hasattr(cust, "invoice_settings") else None),
                },
            ),
        )
        if payload.save_row:
            cur.execute(
                """
                INSERT INTO PAYMENT_METHOD (PM_ID, DONOR_ID, STRIPE_CUSTOMER_ID, STRIPE_PAYMENT_METHOD_ID, USAGE)
                SELECT %s, %s, %s, %s, %s
                """,
                (
                    f"pm-{payload.payment_method_id}",
                    payload.donor_id,
                    payload.customer_id,
                    payload.payment_method_id,
                    "OFF_SESSION",
                ),
            )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    return {
        "ok": True,
        "customer_id": payload.customer_id,
        "payment_method_id": payload.payment_method_id,
        "default_payment_method": (
            cust.invoice_settings.default_payment_method if hasattr(cust, "invoice_settings") else None
        ),
    }
    
@app.post("/payment_method/attach")
def attach_payment_method(payload: PaymentMethodAttachIn):
    print(f"=== BACKEND DEBUG: Attempting to attach payment method ===")
    print(f"=== BACKEND DEBUG: customer_id = {payload.customer_id} ===")
    print(f"=== BACKEND DEBUG: payment_method_id = {payload.payment_method_id} ===")
    
    try:
        # First, let's check what type of payment method this is
        pm = stripe.PaymentMethod.retrieve(payload.payment_method_id)
        print(f"=== BACKEND DEBUG: Payment method type = {pm.type} ===")
        print(f"=== BACKEND DEBUG: Payment method object = {pm} ===")
        
        stripe.PaymentMethod.attach(payload.payment_method_id, customer=payload.customer_id)
        print(f"=== BACKEND DEBUG: Successfully attached payment method ===")
        
        stripe.Customer.modify(
            payload.customer_id,
            invoice_settings={"default_payment_method": payload.payment_method_id},
        )
        print(f"=== BACKEND DEBUG: Successfully set as default payment method ===")
        
        cust = stripe.Customer.retrieve(payload.customer_id)
    except Exception as e:
        print(f"=== BACKEND DEBUG: Stripe error: {str(e)} ===")
        print(f"=== BACKEND DEBUG: Error type: {type(e)} ===")
        raise HTTPException(status_code=400, detail=f"Stripe attach PM error: {e}")

    # ... rest of your logging code ...

    return {
        "ok": True,
        "customer_id": payload.customer_id,
        "payment_method_id": payload.payment_method_id,
        "default_payment_method": (
            cust.invoice_settings.default_payment_method if hasattr(cust, "invoice_settings") else None
        ),
    }
    
# ---------- Twilio inbound SMS (YES/NO) ----------
@app.post("/webhook/twilio")
async def twilio_inbound(request: Request):
    """
    Expects Twilio form-encoded webhook (application/x-www-form-urlencoded).
    Validates X-Twilio-Signature if TWILIO_AUTH_TOKEN is set.
    Updates the most recent pending VERIFICATION_SMS row for the sender, else inserts a new row.
    """
    form = dict((await request.form()).items())
    message_sid = form.get("MessageSid")
    from_num = form.get("From")
    body_raw = form.get("Body") or ""
    body = body_raw.strip().lower()
    session_id = form.get("SessionId") or None
    donor_id = form.get("DonorId") or None

    # Signature validation
    if twilio_validator:
        url = str(request.url)
        signature = request.headers.get("X-Twilio-Signature")
        if not signature or not twilio_validator.validate(url, form, signature):
            raise HTTPException(status_code=403, detail="Invalid Twilio signature")

    # Normalize YES/NO
    result = "INVALID"
    if body in {"y", "yes", "oui"}:
        result = "YES"
    elif body in {"n", "no", "non"}:
        result = "NO"

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        # Match most recent outbound row for this sender with no inbound yet
        row = cur.execute(
            """
            SELECT VERIF_ID, SESSION_ID, DONOR_ID
            FROM VERIFICATION_SMS
            WHERE MOBILE_E164 = %s AND INBOUND_TS IS NULL
            ORDER BY SENT_TS DESC
            LIMIT 1
            """,
            (from_num,),
        ).fetchone()

        if row:
            verif_id, session_id_db, donor_id_db = row
            cur.execute(
                """
                UPDATE VERIFICATION_SMS
                SET INBOUND_TS = CURRENT_TIMESTAMP(),
                    INBOUND_BODY = %s,
                    RESULT = %s,
                    TWILIO_MSG_SID = COALESCE(TWILIO_MSG_SID, %s)
                WHERE VERIF_ID = %s
                """,
                (body, result, message_sid, verif_id),
            )
            session_id = session_id or session_id_db
            donor_id = donor_id or donor_id_db
        else:
            # No match → insert standalone inbound
            cur.execute(
                """
                INSERT INTO VERIFICATION_SMS
                (VERIF_ID, SESSION_ID, DONOR_ID, SENT_TS, MESSAGE_BODY,
                 INBOUND_TS, INBOUND_BODY, RESULT, TWILIO_MSG_SID, MOBILE_E164)
                SELECT %s, %s, %s, NULL, NULL,
                       CURRENT_TIMESTAMP(), %s, %s, %s, %s
                """,
                (
                    f"tw-{message_sid or datetime.now(timezone.utc).timestamp()}",
                    session_id,
                    donor_id,
                    body,
                    result,
                    message_sid,
                    from_num,
                ),
            )

        insert_event(
            cur,
            LogEventIn(
                event_type=f"SMS_REPLY_{result}",
                session_id=session_id,
                donor_id=donor_id,
                attributes={"from": from_num, "body": body_raw, "message_sid": message_sid},
            ),
        )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

    # TwiML response
    text = "Thanks! Please proceed on the tablet." if result != "INVALID" else "Sorry, please reply YES or NO."
    return PlainTextResponse(
        f"""<?xml version="1.0" encoding="UTF-8"?><Response><Message>{text}</Message></Response>""",
        media_type="application/xml",
    )

# ---------- UI Routing ----------
@app.post("/fundraiser/login", response_model=FundraiserLoginOut)
def fundraiser_login(payload: FundraiserLoginIn):
    """
    Look up fundraiser, join charity/campaign, start a session, log it, return branding payload.
    """
    session_id = f"sess-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')}"
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        cur.execute(
            """
            SELECT FUNDRAISER_ID, DISPLAY_NAME, EMAIL, ACTIVE, CHARITY_ID, CAMPAIGN_ID
            FROM FUNDRAISER
            WHERE FUNDRAISER_ID = %s AND COALESCE(ACTIVE, TRUE) = TRUE
            """,
            (payload.fundraiser_id,),
        )
        fund = row_to_dict(cur, cur.fetchone())
        if not fund:
            raise HTTPException(status_code=404, detail="Fundraiser not found or inactive")
        
        charity = None
        if fund.get("CHARITY_ID"):
            cur.execute(
                """
                SELECT CHARITY_ID, NAME, BRAND_PRIMARY_HEX, LOGO_URL, BLURB, TERMS_URL, COUNTRY
                FROM CHARITY WHERE CHARITY_ID = %s
                """,
                (fund["CHARITY_ID"],),
            )
            charity = row_to_dict(cur, cur.fetchone())
            print(f"=== DEBUG: Raw charity data: {charity} ===")
            
        # Handle logo URL presigning with debugging
        if charity and (charity.get("LOGO_URL") or "").startswith("@"):
            try:
                original_url = charity["LOGO_URL"]
                print(f"=== DEBUG: Original logo URL: {original_url} ===")
                
                presigned = presign_stage_url(cur, charity["LOGO_URL"], expires_sec=3600)
                print(f"=== DEBUG: Presigned URL result: {presigned} ===")
                
                if presigned:
                    charity["LOGO_URL"] = presigned
                    print(f"=== DEBUG: Updated charity LOGO_URL to: {charity['LOGO_URL']} ===")
                else:
                    print(f"=== DEBUG: Presign returned None, keeping original: {original_url} ===")
            except Exception as e:
                print(f"=== DEBUG: Presign failed with error: {e} ===")
                # Don't crash login if presign fails; just leave the original value
                pass
        elif charity:
            print(f"=== DEBUG: Logo URL doesn't start with @: {charity.get('LOGO_URL')} ===")
        else:
            print("=== DEBUG: No charity found ===")

        # Rest of your existing code...
        campaign = None
        if fund.get("CAMPAIGN_ID"):
            cur.execute(
                """
                SELECT CAMPAIGN_ID, CHARITY_ID, NAME, START_DATE, END_DATE, MONTHLY_DEFAULT,
                       PRESET_AMOUNTS, MIN_AMOUNT, CURRENCY
                FROM CAMPAIGN WHERE CAMPAIGN_ID = %s
                """,
                (fund["CAMPAIGN_ID"],),
            )
            campaign = row_to_dict(cur, cur.fetchone())
            
        cur.execute(
            """
            INSERT INTO SESSION (SESSION_ID, FUNDRAISER_ID, CHARITY_ID, CAMPAIGN_ID, STATE, DEVICE_ID, CREATED_AT)
            SELECT %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP()
            """,
            (
                session_id,
                fund["FUNDRAISER_ID"],
                fund.get("CHARITY_ID"),
                fund.get("CAMPAIGN_ID"),
                "STARTED",
                os.getenv("APP_DEVICE_ID", None),
            ),
        )
        
        insert_event(
            cur,
            LogEventIn(
                event_type="SESSION_STARTED",
                session_id=session_id,
                fundraiser_id=fund["FUNDRAISER_ID"],
                attributes={"fundraiser": fund, "charity": charity, "campaign": campaign},
            ),
        )
        
        print(f"=== DEBUG: Final charity object being returned: {charity} ===")
        
        return FundraiserLoginOut(
            session_id=session_id,
            fundraiser=fund,
            charity=charity,
            campaign=campaign,
        )
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Testing endpoint for images ---------- 
@app.get("/test/presign")
def test_presign_direct():
    """Test endpoint to debug presigning"""
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        
        # Test with your exact stage URI
        test_uri = "@PHOENIX_APP_DEV.CORE.ASSETS/logos/CH003.png"
        print(f"=== DEBUG: Testing presign for: {test_uri} ===")
        
        presigned_url = presign_stage_url(cur, test_uri, expires_sec=3600)
        print(f"=== DEBUG: Presign result: {presigned_url} ===")
        
        return {
            "original": test_uri,
            "presigned": presigned_url,
            "success": presigned_url is not None
        }
    except Exception as e:
        print(f"=== DEBUG: Test presign failed: {e} ===")
        return {
            "error": str(e),
            "success": False
        }
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Donor to database ----------
@app.post("/donor/upsert")
def donor_upsert(d: DonorUpsertIn):
    """
    Create/update donor record; enforce 25+ by DOB; return donor_id.
    Also records DONOR_SESSION with CHARITY_ID/CAMPAIGN_ID snapshot from SESSION.
    """
    # basic required checks (middle/address2 optional)
    required_fields = {
        "first_name": d.first_name, "last_name": d.last_name, "dob_iso": d.dob_iso,
        "mobile_e164": d.mobile_e164, "email": d.email, "address1": d.address1,
        "city": d.city, "region": d.region, "postal_code": d.postal_code, "country": d.country
    }
    missing = [k for k, v in required_fields.items() if not _nz(v)]
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing required fields: {', '.join(missing)}")

    # age check
    try:
        dob = datetime.fromisoformat(d.dob_iso).date()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid dob_iso format (expected YYYY-MM-DD)")
    today = datetime.now(timezone.utc).date()
    age = (today - dob).days // 365
    if age < 25:
        raise HTTPException(status_code=403, detail="Donor must be at least 25 years old")

    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()

        # Look up by email (canonical)
        cur.execute("SELECT DONOR_ID FROM DONOR WHERE EMAIL = %s", (d.email,))
        row = cur.fetchone()

        if row:
            donor_id = row[0]
            cur.execute(
                """
                UPDATE DONOR SET
                  TITLE=%s, FIRST_NAME=%s, MIDDLE_NAME=%s, LAST_NAME=%s, DOB_DATE=%s,
                  MOBILE_E164=%s, EMAIL=%s, ADDRESS1=%s, ADDRESS2=%s, CITY=%s,
                  REGION=%s, POSTAL_CODE=%s, COUNTRY=%s, UPDATED_AT=CURRENT_TIMESTAMP()
                WHERE DONOR_ID=%s
                """,
                (
                    d.title, d.first_name, d.middle_name, d.last_name, d.dob_iso,
                    d.mobile_e164, d.email, d.address1, d.address2, d.city,
                    d.region, d.postal_code, d.country, donor_id
                ),
            )
            action = "UPDATE"
        else:
            donor_id = f"donor-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')}"
            cur.execute(
                """
                INSERT INTO DONOR (DONOR_ID, TITLE, FIRST_NAME, MIDDLE_NAME, LAST_NAME, DOB_DATE,
                                   MOBILE_E164, EMAIL, ADDRESS1, ADDRESS2, CITY, REGION, POSTAL_CODE, COUNTRY, CREATED_AT)
                SELECT %s,%s,%s,%s,%s,%s,
                       %s,%s,%s,%s,%s,%s,%s,%s, CURRENT_TIMESTAMP()
                """,
                (
                    donor_id, d.title, d.first_name, d.middle_name, d.last_name, d.dob_iso,
                    d.mobile_e164, d.email, d.address1, d.address2, d.city,
                    d.region, d.postal_code, d.country
                ),
            )
            action = "INSERT"

        # Pull session’s campaign/charity snapshot
        cur.execute(
            """
            SELECT CHARITY_ID, CAMPAIGN_ID
            FROM SESSION
            WHERE SESSION_ID = %s
            """,
            (d.session_id,),
        )
        sess_meta = cur.fetchone()
        charity_id = sess_meta[0] if sess_meta else None
        campaign_id = sess_meta[1] if sess_meta else None

        # Record donor-session (with campaign/charity)
        cur.execute(
            """
            INSERT INTO DONOR_SESSION (SESSION_ID, DONOR_ID, FUNDRAISER_ID, CHARITY_ID, CAMPAIGN_ID, CREATED_AT)
            SELECT %s, %s, %s, %s, %s, CURRENT_TIMESTAMP()
            """,
            (d.session_id, donor_id, d.fundraiser_id, charity_id, campaign_id)
        )

        insert_event(
            cur,
            LogEventIn(
                event_type=f"DONOR_{action}",
                session_id=d.session_id,
                donor_id=donor_id,
                fundraiser_id=d.fundraiser_id,
                attributes={"email": d.email, "mobile": d.mobile_e164},
            ),
        )
        return {"donor_id": donor_id}
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Donor Details ----------  
@app.get("/donor/{donor_id}")
def get_donor(donor_id: str):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        cur.execute(
            """
            SELECT TITLE, FIRST_NAME, MIDDLE_NAME, LAST_NAME, EMAIL, MOBILE_E164
            FROM DONOR 
            WHERE DONOR_ID = %s
            """,
            (donor_id,)
        )
        row = cur.fetchone()
        if row:
            title, first, middle, last, email, phone = row
            full_name = " ".join(filter(None, [title, first, middle, last]))
            return {
                "email": email,
                "name": full_name,
                "phone": phone
            }
        else:
            raise HTTPException(status_code=404, detail="Donor not found")
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Products By Campaign ----------
@app.get("/products/campaign/{campaign_id}")
def get_campaign_products(campaign_id: str):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        cur.execute(
            """
            SELECT PRODUCT_ID, PRODUCT_TYPE, AMOUNT_CENTS, CURRENCY, 
                   DISPLAY_NAME, STRIPE_PRICE_ID, ACTIVE
            FROM PRODUCT 
            WHERE CAMPAIGN_ID = %s AND ACTIVE = TRUE
            ORDER BY PRODUCT_TYPE, AMOUNT_CENTS
            """,
            (campaign_id,)
        )
        rows = cur.fetchall()
        products = []
        for row in rows:
            products.append({
                "product_id": row[0],
                "product_type": row[1], 
                "amount_cents": int(row[2]) if row[2] else 0,
                "currency": row[3],
                "display_name": row[4],
                "stripe_price_id": row[5],
                "active": bool(row[6])
            })
        return {"products": products}
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()
        
# ---------- Communication Preferences ----------
@app.post("/donor/consent")
def donor_consent_update(body: DonorConsentIn):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        # Update donor consents
        cur.execute(
            """
            UPDATE DONOR
            SET CONSENT_SMS = %s,
                CONSENT_EMAIL = %s,
                CONSENT_MAIL = %s,
                UPDATED_AT = CURRENT_TIMESTAMP()
            WHERE DONOR_ID = %s
            """,
            (body.consent_sms, body.consent_email, body.consent_mail, body.donor_id),
        )

        insert_event(
            cur,
            LogEventIn(
                event_type="DONOR_CONSENT_UPDATE",
                session_id=body.session_id,
                donor_id=body.donor_id,
                attributes={
                    "consent_sms": body.consent_sms,
                    "consent_email": body.consent_email,
                    "consent_mail": body.consent_mail,
                },
            ),
        )
        return {"ok": True}
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Products & price ids ----------
@app.get("/products/lookup")
def lookup_product(
    campaign_id: str,
    amount_cents: int,
    currency: str,
    product_type: str = "MONTHLY"
):
    ctx = get_snowflake_ctx()
    try:
        cur = ctx.cursor()
        cur.execute(
            """
            SELECT STRIPE_PRICE_ID, PRODUCT_ID, DISPLAY_NAME
            FROM PRODUCT 
            WHERE CAMPAIGN_ID = %s 
              AND AMOUNT_CENTS = %s 
              AND UPPER(CURRENCY) = UPPER(%s)
              AND UPPER(PRODUCT_TYPE) = UPPER(%s)
              AND ACTIVE = TRUE
            LIMIT 1
            """,
            (campaign_id, amount_cents, currency, product_type)
        )
        row = cur.fetchone()
        if row:
            return {
                "stripe_price_id": row[0],
                "product_id": row[1], 
                "display_name": row[2]
            }
        else:
            raise HTTPException(status_code=404, detail="No matching product found")
    finally:
        try: cur.close()
        except Exception: pass
        ctx.close()

# ---------- Register tablet to stripe ----------
@app.post("/terminal/register_device")
def register_device():
    try:
        # Get device info from request
        data = request.get_json()
        device_code = data.get('device_code')
        location_id = data.get('location_id', 'tml_GMwgTw8OHAJtnR')  # Your location ID
        
        # Register device with Stripe
        reader = stripe.terminal.Reader.create(
            registration_code=device_code,
            location=location_id,
            label="Donation Tablet"  # Optional friendly name
        )
        
        return {
            "reader_id": reader.id,
            "status": reader.status,
            "device_type": reader.device_type
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Device registration error: {e}")
        
# ---------- Signature Upload ----------
@app.post("/signature/upload", response_model=SignatureUploadOut)
def upload_signature(payload: SignatureUploadIn):
    try:
        timestamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')
        signature_id = f"sig-{timestamp}-{payload.donor_id}"

        # Decode and hash
        # If Android ever sends a data URI, strip it; this is safe either way
        b64 = payload.signature_data.split(",", 1)[-1]
        png_data = base64.b64decode(b64)
        hash_sha256 = hashlib.sha256(png_data).hexdigest()

        # Build stage paths (INTERNAL stage!)
        rel_path = f"signatures/{signature_id}.png"
        full_stage_uri = f"{SIGNATURE_STAGE_URI_PREFIX}/{rel_path}"  # @PHOENIX_APP_DEV.CORE.ASSETS_INT/signatures/...

        ctx = get_snowflake_ctx()
        try:
            cur = ctx.cursor()

            # Write temp file with final name so PUT lands exactly where we want
            import tempfile, os
            with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                tmp.write(png_data)
                temp_file_path = tmp.name

            try:
                # PUT works ONLY on INTERNAL stages — which ASSETS_INT is
                cur.execute(
                    f"PUT file://{temp_file_path} {SIGNATURE_STAGE_URI_PREFIX}/signatures/ "
                    "AUTO_COMPRESS=FALSE OVERWRITE=TRUE"
                )
            finally:
                try:
                    os.unlink(temp_file_path)
                except Exception:
                    pass

            # Store metadata
            cur.execute(
                """
                INSERT INTO SIGNATURE (SIGNATURE_ID, DONOR_ID, SESSION_ID, SIGNATURE_IMAGE, HASH_SHA256, CAPTURED_AT)
                VALUES (%s, %s, %s, %s, %s, CURRENT_TIMESTAMP())
                """,
                (signature_id, payload.donor_id, payload.session_id, full_stage_uri, hash_sha256)
            )

            # Presign the uploaded file (works fine for internal stages)
            signature_url = presign_stage_url(cur, full_stage_uri, expires_sec=3600) or ""

            insert_event(
                cur,
                LogEventIn(
                    event_type="SIGNATURE_CAPTURED",
                    session_id=payload.session_id,
                    donor_id=payload.donor_id,
                    attributes={
                        "signature_id": signature_id,
                        "hash_sha256": hash_sha256,
                        "file_size": len(png_data),
                        "stage_path": rel_path,
                        "stage": SIGNATURE_STAGE_NAME,
                    },
                ),
            )

            return SignatureUploadOut(
                signature_id=signature_id,
                signature_url=signature_url,
                success=True,
            )

        finally:
            try: cur.close()
            except Exception: pass
            ctx.close()

    except Exception as e:
        print(f"=== DEBUG: Signature upload failed: {e} ===")
        raise HTTPException(status_code=500, detail=f"Signature upload failed: {str(e)}")

# ---------- Stripe Location ID ----------
@app.get("/terminal/location")
def get_terminal_location():
    return {"location_id": os.getenv("STRIPE_TERMINAL_LOCATION_ID", "")}
