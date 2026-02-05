import os
import json
import logging
from pathlib import Path

# Install SDK:  pip install 'volcengine-python-sdk[ark]'
from volcenginesdkarkruntime import Ark

# 日志：与脚本同目录
_log_dir = Path(__file__).resolve().parent
_log_file = _log_dir / "script2episode.log"
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(_log_file, encoding="utf-8"),
        logging.StreamHandler(),
    ],
)
logger = logging.getLogger(__name__)

# 读取 system.md 文件内容
with open('script_analysis.md', 'r', encoding='utf-8') as f:
    system_content = f.read().strip()

# 读取剧本文件内容
with open('绝境先知-黄金瞳.txt', 'r', encoding='utf-8') as f:
    script_content = f.read().strip()

client = Ark(
    # The base URL for model invocation
    base_url="https://ark.cn-beijing.volces.com/api/v3",
    # Get API Key：https://console.volcengine.com/ark/region:ark+cn-beijing/apikey
    api_key=os.getenv('ARK_API_KEY',"1f70c7d1-384a-4246-b9b0-8f4053f7f478")
)

# 创建一个对话请求
completion = client.chat.completions.create(
    model="deepseek-v3-1-terminus",
    messages=[
        {"role": "system", "content": system_content},
        {"role": "user", "content": script_content}
    ],
    thinking={"type": "disabled"},
    max_tokens=32768,  # 模型最大回答 32k，默认 4k 易截断
)

logger.info(completion.choices[0].message.content)
