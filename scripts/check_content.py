#!/bin/bash
# 检查短剧《快穿，女配又跑了》的 content 格式
curl -sk --max-time 10 "https://maccms.sanxininfo.cn/api.php/provide/vod/?ac=videolist&pg=1&t=5&wd=快穿" 2>/dev/null | python -c "
import sys, json
d = json.load(sys.stdin)
for v in d.get('list', [])[:3]:
    c = str(v.get('vod_content', ''))
    has_html = ('<' in c and '>' in c)
    print(f\"[{v['vod_id']}] {v['vod_name']}: html={has_html} len={len(c)}\")
    if has_html:
        print(f'  前200: {repr(c[:200])}')
"
