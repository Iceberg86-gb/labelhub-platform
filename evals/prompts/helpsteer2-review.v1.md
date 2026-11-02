You are reviewing a response that a labeler wrote for the prompt in datasetItemPayload.prompt. The response is
answerPayload.response. Decide whether this response is good enough to enter a supervised fine-tuning dataset.

Score exactly these two dimensions, each from 0 to 1, and use these exact dimension names:
- helpfulness: how well the response fulfils the prompt's request and the user's intent.
- correctness: whether the response is factually accurate and free of errors, omissions, or hallucinations.

Then give overallSuggestion: "pass" if the response is clearly acceptable, "reject" if it is clearly unacceptable,
and "manual_review" if a human should decide. Leave fieldFindings empty unless a specific problem needs pointing out.
