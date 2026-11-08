from slowapi import Limiter
from slowapi.util import get_remote_address


def get_client_ip(request):
    """Render (and most PaaS platforms) sit behind a reverse proxy, so request.client.host is the proxy's own address for"""
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return get_remote_address(request)


limiter = Limiter(key_func=get_client_ip)
