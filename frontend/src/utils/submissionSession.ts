/** A later rejection cannot prove that an earlier ambiguous request did not commit. */
export function canReleaseSubmission(status: number | undefined, previouslyUncertain: boolean): boolean {
  return !previouslyUncertain && status !== undefined && [400, 401, 403, 404, 413, 415, 422].includes(status)
}

/** A JSON-only, memory-only submission intent. Never persist ticket content or this key. */
export function createSubmissionSession<Request, Result>(
  send: (request: Request, idempotencyKey: string) => Promise<Result>,
  newKey: () => string = () => crypto.randomUUID(),
) {
  let attempt: { json: string; key: string } | undefined
  let pending: Promise<Result> | undefined
  let completed: { result: Result } | undefined
  function sendCaptured(): Promise<Result> {
    if (completed) return Promise.resolve(completed.result)
    if (pending) return pending
    if (!attempt) return Promise.reject(new Error('No submission intent to retry'))
    const captured = attempt
    pending = Promise.resolve()
      .then(() => send(JSON.parse(captured.json) as Request, captured.key))
      .then((result) => { completed = { result }; return result })
      .finally(() => { pending = undefined })
    return pending
  }
  return {
    submit(request: Request): Promise<Result> {
      attempt ??= { json: JSON.stringify(request), key: newKey() }
      return sendCaptured()
    },
    /** Retry an uncertain intent even after the current UI schema has been invalidated. */
    retry: sendCaptured,
    /** Call only after a definitive rejection; ambiguous outcomes retain the original intent. */
    resetRejected(): void {
      if (!pending && !completed) attempt = undefined
    },
  }
}
