Validation Authority Policy (M7-P2 C4)

payloadValidation.ts is the SUBMIT-TIME AUTHORITY for all submissions.
Formily x-validator is a LIVE UI FEEDBACK projection of the subset
of rules expressible natively in Formily, used to give users typing
feedback without round-tripping to the server.

The contract is asymmetric by design:
- Formily UI rules MAY be less strict than payloadValidation rules
  (e.g., Formily doesn't enforce cross-field constraints)
- Formily UI rules MUST NOT be more strict than payloadValidation
  (a value that passes Formily must also pass payloadValidation;
  this is verified by the round-trip test below)

Submit path: Formily form.values -> formilyValuesToAnswerPayload
-> payloadValidation -> persist. If payloadValidation rejects a
value that Formily accepted, the user sees a server-side error.
This is acceptable because:
1. The complete validation source of truth is payloadValidation
2. Formily UI feedback is best-effort for common typos / format
3. Server-side rejection is reachable via existing error display
   paths (errors prop on SchemaFormilyRenderer)
