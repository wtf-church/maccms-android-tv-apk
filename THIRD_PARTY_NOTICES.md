# Third-Party Notices

本项目包含以下第三方开源代码。原始版权与许可证声明如下。

## lemonTV（M3U / IPTV 解析逻辑）

- 项目：https://github.com/jia070310/lemontv
- 参考文件：`app/src/main/java/top/yogiczy/mytv/data/repositories/iptv/parser/M3uIptvParser.kt`
- 本项目适配文件：`app-android/app/src/main/java/com/maccms/tv/data/M3uPlaylistParser.kt`
- 许可证：MIT
- 版权：Copyright (c) 2020-PRESENT yaoxieyoulei

适配说明：在原实现基础上改为状态机逐行解析（兼容 `#EXTVLCOPT` 位于 `#EXTINF` 与 URL 之间）、
增加 BOM/CRLF 兼容、HTTP(S) URL 校验与去重、按频道名合并备用线路、按名称推断分组，
并调整了数据模型与错误提示。

```
MIT License

Copyright (c) 2020-PRESENT yaoxieyoulei

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
