<?php
declare(strict_types=1);

/**
 * Puppy Clicker support-report relay.
 *
 * Server environment variables:
 *   PUPPY_DISCORD_TELEMETRY_WEBHOOK
 *   PUPPY_DISCORD_CRASH_WEBHOOK
 *   PUPPY_DISCORD_USER_REPORT_WEBHOOK
 *
 * Never put those Discord webhook URLs in the Android app or this repository.
 */

header('Content-Type: application/json; charset=utf-8');

const PUPPY_APP_NAME = 'Puppy Clicker';
const PUPPY_APP_LOGO_URL = 'https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/logos/puppy_clicker.png';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['ok' => false, 'error' => 'method_not_allowed']);
    exit;
}

$raw = file_get_contents('php://input');
if ($raw === false || $raw === '' || strlen($raw) > 20000) {
    http_response_code(400);
    echo json_encode(['ok' => false, 'error' => 'invalid_body']);
    exit;
}

$data = json_decode($raw, true);
if (!is_array($data)) {
    http_response_code(400);
    echo json_encode(['ok' => false, 'error' => 'invalid_json']);
    exit;
}

$kind = isset($data['kind']) && is_string($data['kind']) ? $data['kind'] : '';
if (!in_array($kind, ['telemetry', 'crash', 'user_report'], true)) {
    http_response_code(400);
    echo json_encode(['ok' => false, 'error' => 'invalid_kind']);
    exit;
}

$webhook = match ($kind) {
    'crash' => getenv('PUPPY_DISCORD_CRASH_WEBHOOK'),
    'user_report' => getenv('PUPPY_DISCORD_USER_REPORT_WEBHOOK'),
    default => getenv('PUPPY_DISCORD_TELEMETRY_WEBHOOK'),
};

if (!is_string($webhook) || !str_starts_with($webhook, 'https://discord.com/api/webhooks/')) {
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'destination_not_configured']);
    exit;
}

$ip = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
$bucket = hash('sha256', $ip . '|' . $kind);
$rateFile = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR)
    . DIRECTORY_SEPARATOR . 'puppy-report-' . $bucket;
$now = time();
$last = is_file($rateFile) ? (int) @file_get_contents($rateFile) : 0;
$minimumInterval = $kind === 'user_report' ? 8 : 2;
if ($last > 0 && ($now - $last) < $minimumInterval) {
    http_response_code(429);
    echo json_encode(['ok' => false, 'error' => 'rate_limited']);
    exit;
}
@file_put_contents($rateFile, (string) $now, LOCK_EX);

$clean = static function ($value, int $max): string {
    if (!is_string($value) && !is_numeric($value)) {
        return '';
    }
    $text = preg_replace('/[\x00-\x1F\x7F]/u', ' ', (string) $value) ?? '';
    return mb_substr(trim($text), 0, $max);
};

$appVersion = $clean($data['app_version'] ?? '', 64);
$appVersionCode = $clean($data['app_version_code'] ?? '', 32);
$androidSdk = $clean($data['android_sdk'] ?? '', 16);
$timestamp = $clean($data['timestamp_ms'] ?? '', 32);

$fields = [
    ['name' => 'App', 'value' => $appVersion . ' (' . $appVersionCode . ')', 'inline' => true],
    ['name' => 'Android SDK', 'value' => $androidSdk !== '' ? $androidSdk : 'unknown', 'inline' => true],
    ['name' => 'Timestamp', 'value' => $timestamp !== '' ? $timestamp : 'unknown', 'inline' => false],
];

$username = $clean($data['username'] ?? '', 64);
$playerId = $clean($data['player_id'] ?? '', 96);
$friendCode = $clean($data['friend_code'] ?? '', 64);
$discordUserId = $clean($data['discord_user_id'] ?? '', 32);
$discordUsername = $clean($data['discord_username'] ?? '', 64);
$discordDisplayName = $clean($data['discord_display_name'] ?? '', 96);

if ($username !== '') {
    $fields[] = ['name' => 'Puppy Clicker User', 'value' => $username, 'inline' => true];
}
if ($playerId !== '') {
    $fields[] = ['name' => 'Player ID', 'value' => $playerId, 'inline' => true];
}
if ($friendCode !== '') {
    $fields[] = ['name' => 'Friend Code', 'value' => $friendCode, 'inline' => true];
}
if ($discordUsername !== '' || $discordDisplayName !== '' || $discordUserId !== '') {
    $discordValue = $discordDisplayName !== '' ? $discordDisplayName : $discordUsername;
    if ($discordUsername !== '' && $discordUsername !== $discordValue) {
        $discordValue .= ' (@' . $discordUsername . ')';
    }
    if ($discordUserId !== '') {
        $discordValue .= ($discordValue !== '' ? "\n" : '') . 'ID: ' . $discordUserId;
    }
    $fields[] = [
        'name' => 'Discord Identity',
        'value' => $discordValue !== '' ? $discordValue : 'Linked Discord account',
        'inline' => false
    ];
}

if ($kind === 'telemetry') {
    $event = $clean($data['event'] ?? 'unknown', 64);
    $title = '📊 Puppy Clicker Anonymous Diagnostics';
    $description = 'Event: **' . ($event !== '' ? $event : 'unknown') . '**';
    $username = 'Puppy Clicker Diagnostics';
} elseif ($kind === 'user_report') {
    $reportId = $clean($data['report_id'] ?? '', 64);
    $reportType = $clean($data['report_type'] ?? 'Other', 64);
    $subject = $clean($data['subject'] ?? 'User report', 120);
    $body = $clean($data['body'] ?? '', 3500);

    if ($reportId === '' || $body === '') {
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'invalid_user_report']);
        exit;
    }

    $title = '🐾 Puppy Clicker Tier 1 User Report';
    $description = '**' . ($subject !== '' ? $subject : 'User report') . '**';
    $fields[] = ['name' => 'Report ID', 'value' => $reportId, 'inline' => true];
    $fields[] = ['name' => 'Category', 'value' => $reportType !== '' ? $reportType : 'Other', 'inline' => true];
    $fields[] = ['name' => 'Status', 'value' => '🟡 New · Tier 1', 'inline' => true];
    $fields[] = [
        'name' => 'Report',
        'value' => $body !== '' ? $body : 'No report body supplied.',
        'inline' => false
    ];
    $username = 'Puppy Clicker Tier 1 Support';
} else {
    $exception = $clean($data['exception'] ?? 'Unknown exception', 240);
    $message = $clean($data['message'] ?? '', 600);
    $thread = $clean($data['thread'] ?? '', 120);
    $stack = $clean($data['stack'] ?? '', 900);

    $title = '🐛 Puppy Clicker Crash Report';
    $description = '**' . ($exception !== '' ? $exception : 'Unknown exception') . '**';
    if ($message !== '') {
        $description .= "\n" . $message;
    }
    if ($thread !== '') {
        $fields[] = ['name' => 'Thread', 'value' => $thread, 'inline' => true];
    }
    if ($stack !== '') {
        $fields[] = [
            'name' => 'Stack trace',
            'value' => $stack,
            'inline' => false
        ];
    }
    $username = 'Puppy Clicker Crash Handler';
}

$discordPayload = json_encode([
    'username' => PUPPY_APP_NAME . ' Support',
    'avatar_url' => PUPPY_APP_LOGO_URL,
    'allowed_mentions' => ['parse' => []],
    'embeds' => [[
        'author' => [
            'name' => PUPPY_APP_NAME,
            'icon_url' => PUPPY_APP_LOGO_URL
        ],
        'thumbnail' => ['url' => PUPPY_APP_LOGO_URL],
        'title' => $title,
        'description' => $description,
        'fields' => $fields,
        'footer' => [
            'text' => $kind === 'user_report'
                ? 'Puppy Clicker Tier 1 Support · User-submitted report'
                : 'Consent-gated Puppy Clicker support reporting'
        ]
    ]]
], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

if ($discordPayload === false) {
    http_response_code(500);
    echo json_encode(['ok' => false, 'error' => 'encode_failed']);
    exit;
}

$ch = curl_init($webhook);
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $discordPayload,
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 3,
    CURLOPT_TIMEOUT => 5,
]);

curl_exec($ch);
$status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
$error = curl_error($ch);
curl_close($ch);

if ($status < 200 || $status >= 300) {
    http_response_code(502);
    echo json_encode([
        'ok' => false,
        'error' => 'discord_delivery_failed',
        'status' => $status,
        'detail' => $error !== '' ? 'transport_error' : 'upstream_rejected'
    ]);
    exit;
}

echo json_encode(['ok' => true]);
