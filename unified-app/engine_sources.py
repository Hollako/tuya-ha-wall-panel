"""Resolve the canonical SIP core and product-specific Android adapters."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def sip_source(name):
    shared=ROOT.parent/'shared/sip/src/local/wallpanel/app'/name
    local=ROOT/'src/local/wallpanel/app'/name
    if shared.is_file() and local.is_file():raise RuntimeError('Duplicate SIP engine source: '+name)
    result=shared if shared.is_file() else local
    if not result.is_file():raise FileNotFoundError(name)
    return result
