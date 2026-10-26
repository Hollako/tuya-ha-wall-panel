"""HTTP/HTTPS dashboard configuration validation shared by delivery and UI."""
from urllib.parse import urlsplit

def validate_dashboard_url(value):
    if not isinstance(value,str):raise ValueError("Enter an HTTP or HTTPS dashboard URL")
    value=value.strip()
    if not value:return ""
    if len(value)>255 or any(ord(c)<=32 or ord(c)==127 for c in value):
        raise ValueError("Dashboard URL must be at most 255 characters with no spaces or control characters")
    try:
        parsed=urlsplit(value)
        if parsed.scheme.lower() not in ("http","https") or not parsed.hostname or parsed.username is not None or parsed.password is not None:
            raise ValueError("Use an HTTP or HTTPS URL without embedded credentials")
        if parsed.port is not None and not 1<=parsed.port<=65535:raise ValueError("Invalid URL port")
    except (ValueError,TypeError) as err:
        raise ValueError("Use a valid HTTP or HTTPS dashboard URL without embedded credentials") from err
    return value
