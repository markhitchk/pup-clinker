import { HttpError } from "./pupeye.ts";

/** Allow HTTPS public image URLs only. Never fetch the supplied URL server-side. */
export function normalizePublicAvatarUrl(value: unknown): string | null {
  if (value === null) return null;
  if (typeof value !== "string" || !value.trim() || value.length > 2048) {
    throw new HttpError(400, "PUP_AVATAR_URL_INVALID", "Enter a public HTTPS image URL.");
  }
  try {
    const url = new URL(value.trim());
    const host = url.hostname.toLowerCase();
    if (url.protocol !== "https:" || url.username || url.password || url.port ||
        !host.includes(".") || /^[\d.]+$/.test(host) || host.startsWith("[") ||
        host === "localhost" || host.endsWith(".localhost") ||
        host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test")) {
      throw new Error("Unsafe avatar address");
    }
    // Page/gallery links cannot be rendered as profile pictures.
    if (["imgur.com", "www.imgur.com", "m.imgur.com", "imgbb.com", "www.imgbb.com",
         "ibb.co", "www.ibb.co", "postimg.cc", "www.postimg.cc"].includes(host)) {
      throw new Error("Image hosting page link");
    }
    if (host === "i.imgur.com" && !/\.(png|jpe?g|webp|gif|avif)$/i.test(url.pathname)) {
      throw new Error("Imgur direct image link required");
    }
    return url.href;
  } catch {
    throw new HttpError(400, "PUP_AVATAR_URL_INVALID",
      "Use a direct public HTTPS image link, not a hosting page or private address.");
  }
}
