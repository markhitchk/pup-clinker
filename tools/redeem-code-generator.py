#!/usr/bin/env python3
"""Generate Puppy Clicker PC1 redeem codes.

The private RSA key is intentionally NOT stored in the repository.
Requires OpenSSL on PATH.
"""

import argparse
import base64
import os
import subprocess
import tempfile
import time


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a signed Puppy Clicker redeem code")
    parser.add_argument("--private-key", required=True, help="Path to redeem-private.pem")
    parser.add_argument("--id", required=True, help="Unique code ID, e.g. launch-001")
    parser.add_argument("--type", required=True, choices=["TREATS", "PUPPY"], help="Reward type")
    parser.add_argument("--amount", type=int, default=0, help="Treat amount for TREATS codes")
    parser.add_argument("--value", default="", help="Puppy ID for PUPPY codes")
    parser.add_argument("--expires-in", type=int, default=0, help="Seconds until expiry; 0 = never")
    args = parser.parse_args()

    if not os.path.isfile(args.private_key):
        raise SystemExit("Private key file not found")
    if not args.id.replace("-", "").replace("_", "").isalnum() or not (4 <= len(args.id) <= 64):
        raise SystemExit("ID must be 4-64 letters/numbers/_/-")
    if args.type == "TREATS" and not (1 <= args.amount <= 1_000_000_000):
        raise SystemExit("TREATS amount must be 1..1,000,000,000")
    if args.type == "PUPPY" and args.value not in {"midnight", "cloud"}:
        raise SystemExit("PUPPY value must currently be midnight or cloud")

    expires = 0 if args.expires_in <= 0 else int(time.time()) + args.expires_in
    amount = args.amount if args.type == "TREATS" else 0
    value = args.value if args.type == "PUPPY" else ""
    payload = f"{args.id}|{args.type}|{amount}|{value}|{expires}".encode("utf-8")

    with tempfile.NamedTemporaryFile(delete=False) as payload_file:
        payload_file.write(payload)
        payload_path = payload_file.name
    signature_path = payload_path + ".sig"

    try:
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", args.private_key, "-out", signature_path, payload_path],
            check=True,
        )
        with open(signature_path, "rb") as handle:
            signature = handle.read()
    finally:
        for path in (payload_path, signature_path):
            try:
                os.remove(path)
            except FileNotFoundError:
                pass

    print(f"PC1.{b64url(payload)}.{b64url(signature)}")


if __name__ == "__main__":
    main()
