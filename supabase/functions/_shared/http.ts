export function json(status: number, body: Record<string, unknown>): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

export class HttpError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) {
    console.error("[PupEye]", error.code, error.message);
    return json(error.status, {
      code: error.code,
      message: error.message,
      ...error.details,
    });
  }
  const message = error instanceof Error ? error.message : "Unexpected Pupeye backend error";
  console.error("[PupEye] PUPEYE_REQUEST_INVALID", message);
  return json(400, { code: "PUPEYE_REQUEST_INVALID", message });
}
