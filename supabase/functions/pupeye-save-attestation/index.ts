Deno.serve(() =>
  new Response(
    JSON.stringify({
      code: "ENDPOINT_RETIRED",
      message: "This legacy endpoint is retired. Pup Account synchronization is the supported progress system.",
    }),
    {
      status: 410,
      headers: { "content-type": "application/json; charset=utf-8" },
    },
  )
);
