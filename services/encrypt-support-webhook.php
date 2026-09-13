<?php
declare(strict_types=1);

/**
 * Encrypt a Discord webhook for Puppy Clicker's Tier 1 support relay.
 *
 * Usage:
 *   php services/encrypt-support-webhook.php
 *
 * Paste the webhook when prompted. The script prints:
 *   PUPPY_DISCORD_USER_REPORT_WEBHOOK_ENC
 *   PUPPY_SUPPORT_WEBHOOK_KEY_B64
 *
 * Store both as server secrets/environment variables. Do not commit either value.
 */

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "CLI only.
");
    exit(1);
}

if (!function_exists('openssl_encrypt')) {
    fwrite(STDERR, "OpenSSL extension is required.
");
    exit(1);
}

fwrite(STDOUT, "Discord webhook URL: ");
$webhook = trim((string) fgets(STDIN));

if (!str_starts_with($webhook, 'https://discord.com/api/webhooks/')) {
    fwrite(STDERR, "Invalid Discord webhook URL.
");
    exit(1);
}

$key = random_bytes(32);
$iv = random_bytes(12);
$tag = '';

$ciphertext = openssl_encrypt(
    $webhook,
    'aes-256-gcm',
    $key,
    OPENSSL_RAW_DATA,
    $iv,
    $tag,
    'puppy-support-webhook-v1',
    16
);

if (!is_string($ciphertext) || strlen($tag) !== 16) {
    fwrite(STDERR, "Unable to encrypt webhook.
");
    exit(1);
}

$encoded = 'v1:'
    . base64_encode($iv)
    . ':'
    . base64_encode($tag)
    . ':'
    . base64_encode($ciphertext);

fwrite(STDOUT, "
Set these only on the support relay server:

");
fwrite(STDOUT, "PUPPY_DISCORD_USER_REPORT_WEBHOOK_ENC=" . $encoded . "
");
fwrite(STDOUT, "PUPPY_SUPPORT_WEBHOOK_KEY_B64=" . base64_encode($key) . "
");
fwrite(STDOUT, "
Do not commit these values to Git or place them in the APK.
");
