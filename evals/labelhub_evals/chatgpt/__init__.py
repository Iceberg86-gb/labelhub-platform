"""Sign in with ChatGPT for local evaluation runs (ADR-018).

Follows OpenAI's open-source / local-app flow: dynamic client registration, PKCE (S256), a
127.0.0.1 loopback callback, and inference against api.openai.com/v1 with store=false + stream=true.
"""
