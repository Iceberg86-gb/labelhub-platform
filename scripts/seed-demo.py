#!/usr/bin/env python3
"""Seed a small, realistic demo through the public API (no SQL): one task walked through the whole pipeline.

Owner creates a task, publishes a schema and a dataset, publishes the task; the labeler claims and submits;
the owner runs AI pre-review (whatever provider the API is configured with; `mock` locally); the reviewer
approves or rejects; the owner issues an MCP token for external agents.

Usage (API running locally, e.g. `make dev-api`):
    python3 scripts/seed-demo.py [--api http://127.0.0.1:8080/api]

Uses the local demo accounts (password demo1234). Never point it at a shared environment.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid

PASSWORD = "demo1234"

TASK = {
    "title": "客服回复质量评审 · 电商售后",
    "description": "评估客服对用户售后问题的回复：是否解决问题、事实是否准确、语气是否得体。用于构建 SFT / 偏好训练数据。",
    "instructionRichText": "阅读用户问题与客服回复，按 Schema 逐项评分。事实错误一律判不合格，拿不准请在备注里说明。",
    "deadlineAt": "2030-01-01T00:00:00Z",
    "quotaTotal": 8,
}

SCHEMA = {
    "fields": [
        {"stableId": "verdict", "label": "整体结论", "type": "single_select", "validation": {"required": True},
         "options": [{"label": "合格", "value": "pass"}, {"label": "不合格", "value": "fail"}]},
        {"stableId": "helpfulness", "label": "有用性（1-5）", "type": "number", "validation": {"required": True, "min": 1, "max": 5}},
        {"stableId": "issues", "label": "问题类型", "type": "multi_select",
         "options": [{"label": "事实错误", "value": "factual"}, {"label": "答非所问", "value": "off_topic"},
                     {"label": "语气不当", "value": "tone"}, {"label": "信息缺失", "value": "missing"}]},
        {"stableId": "note", "label": "备注", "type": "text"},
    ]
}

ITEMS = [
    {"question": "我买的耳机左耳没声音，还在7天无理由期内，可以退吗？",
     "reply": "可以的。您在订单页点击“申请售后”选择“退货退款”，上传问题照片即可，我们会安排上门取件，运费由我们承担。"},
    {"question": "快递显示已签收但我没收到，怎么办？",
     "reply": "请您先联系快递员确认投递位置；如48小时内仍未找到，我们会为您补发或全额退款，您可以随时在这里找我。"},
    {"question": "会员积分什么时候过期？",
     "reply": "积分永久有效，不会过期。"},
    {"question": "发票可以开公司抬头吗？",
     "reply": "可以，在订单详情页“申请发票”中选择“企业”，填写公司名称和税号，电子发票会在3个工作日内发送到您的邮箱。"},
    {"question": "为什么我的优惠券用不了？",
     "reply": "亲亲，这个我们也不太清楚呢，您再试试吧~"},
    {"question": "能不能把收货地址改到另一个城市？",
     "reply": "订单尚未发货时可以修改：在订单详情页点“修改地址”。若已发货，需要联系快递改派，可能产生额外费用。"},
    {"question": "手机充电很慢，是质量问题吗？",
     "reply": "请先确认使用的是原装充电器和数据线；若仍然充电慢，可在保修期内申请检测，确认为质量问题将免费维修或换新。"},
    {"question": "下单后多久发货？",
     "reply": "我们一般30天内发货，具体看仓库心情。"},
]

ANSWERS = [
    {"verdict": "pass", "helpfulness": 5, "issues": [], "note": "流程清晰，承担运费"},
    {"verdict": "pass", "helpfulness": 4, "issues": [], "note": ""},
    {"verdict": "fail", "helpfulness": 2, "issues": ["factual"], "note": "积分规则与平台公示（12个月有效）不符"},
    {"verdict": "pass", "helpfulness": 5, "issues": [], "note": ""},
    {"verdict": "fail", "helpfulness": 1, "issues": ["off_topic", "missing", "tone"], "note": "未排查原因"},
    {"verdict": "pass", "helpfulness": 4, "issues": [], "note": "区分了是否发货"},
]

# (submission index, verdict) for the initial reviewer
REVIEWS = [(0, "approve"), (1, "approve"), (2, "reject"), (3, "approve")]


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None, body=None, raw: bytes | None = None,
             content_type: str = "application/json"):
        data = raw if raw is not None else (None if body is None else json.dumps(body).encode("utf-8"))
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", content_type)
        if token:
            req.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                text = resp.read().decode("utf-8")
                return json.loads(text) if text else None
        except urllib.error.HTTPError as exc:
            raise SystemExit(f"{method} {path} -> {exc.code}: {exc.read().decode('utf-8')[:400]}") from None

    def login(self, username: str) -> str:
        return self.call("POST", "/auth/login", body={"username": username, "password": PASSWORD})["accessToken"]

    def upload_jsonl(self, token: str, task_id: int, rows: list[dict]):
        boundary = uuid.uuid4().hex
        content = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows).encode("utf-8")
        parts = []
        for name, value in (("taskId", str(task_id)), ("format", "jsonl")):
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="items.jsonl"\r\n'
                     f'Content-Type: application/octet-stream\r\n\r\n'.encode() + content + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        return self.call("POST", "/datasets", token, raw=b"".join(parts), content_type=f"multipart/form-data; boundary={boundary}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--api", default="http://127.0.0.1:8080/api")
    args = parser.parse_args()
    api = Api(args.api)

    owner, labeler, reviewer = api.login("owner_demo"), api.login("labeler_demo"), api.login("reviewer_demo")

    task = api.call("POST", "/tasks", owner, TASK)
    task_id = task["id"]
    schema = api.call("POST", "/schemas", owner, {"taskId": task_id, "name": "客服回复质量 v1", "description": "评审 Schema"})
    api.call("POST", f"/schemas/{schema['id']}/versions", owner, {"schemaJson": SCHEMA})
    dataset = api.upload_jsonl(owner, task_id, ITEMS)
    api.call("PATCH", f"/tasks/{task_id}/current-dataset", owner, {"datasetId": dataset["id"]})
    rule = api.call("POST", "/ai-review/rules", owner, {
        "taskId": task_id, "dimensions": ["helpfulness", "correctness"], "passThreshold": 0.8, "rejectThreshold": 0.3,
        "promptTemplate": "评估客服回复是否解决了用户问题（helpfulness）以及事实是否准确（correctness），各给 0-1 分。"
                          "事实错误时 correctness 不高于 0.3。"})
    rule = api.call("POST", f"/ai-review/rules/{rule['id']}/publish", owner)
    prompt_version_id = rule["promptVersionId"]
    api.call("PATCH", f"/tasks/{task_id}/transition", owner, {"toStatus": "published", "reason": "演示数据就绪"})
    print(f"task {task_id} published")

    submissions = []
    for answer in ANSWERS:
        session = api.call("POST", f"/tasks/{task_id}/claim", labeler)
        submission = api.call("POST", f"/sessions/{session['id']}/submit", labeler, {"answerPayload": answer})
        submissions.append(submission["id"])
    print(f"submitted {len(submissions)}: {submissions}")

    for submission_id in submissions:
        api.call("POST", f"/submissions/{submission_id}/ai-review", owner, {"promptVersionId": prompt_version_id})
    print("AI pre-review recorded for every submission")

    for index, verdict in REVIEWS:
        api.call("POST", f"/submissions/{submissions[index]}/ledger-entries", reviewer, {
            "entryType": "reviewer_overall_verdict",
            "payload": {"verdict": verdict, "reviewLevel": "reviewer", "reason": "演示：初审" + ("通过" if verdict == "approve" else "打回")},
        })
    print(f"reviewed {len(REVIEWS)}; {len(submissions) - len(REVIEWS)} left in the queue")

    issued = api.call("POST", f"/tasks/{task_id}/mcp-tokens", owner, {"name": "claude-code-demo", "expiresInDays": 30})
    print(f"MCP token issued: {issued['token']['tokenPrefix']}… (plaintext shown once, not printed)")
    print(f"done: open http://127.0.0.1:5173/owner/tasks/{task_id}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
