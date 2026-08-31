from slowapi import Limiter
from slowapi.util import get_remote_address


def get_client_ip(request):
    """
    Render (and most PaaS platforms) sit behind a reverse proxy, so
    request.client.host is the proxy's own address for every visitor —
    not the real caller. That collapses rate limits into one shared
    bucket for the whole site instead of one per person. Render sets
    X-Forwarded-For correctly, so prefer that; fall back to the raw
    remote address for local/dev runs where there's no proxy at all.
    """
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return get_remote_address(request)


limiter = Limiter(key_func=get_client_ip)