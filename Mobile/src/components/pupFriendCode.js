// Friend Codes are generated server-side. Check only their shape here;
// the protected Exchange service verifies account standing and ownership.
export const normalizePupFriendCode=value=>
 typeof value==='string'?value.trim().toUpperCase():'';
const FRIEND_CODE_PATTERN=/^(?:PUP-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}|PUP-HARLEYTG-DEV)$/;
export const isValidPupFriendCode=value=>
 FRIEND_CODE_PATTERN.test(normalizePupFriendCode(value));
