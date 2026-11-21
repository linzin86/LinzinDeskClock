<div align="center">

<img width="128" alt="app_icon_full" src="https://github.com/user-attachments/assets/45122dc8-5e49-4deb-9722-7f205d00f7d7" />

# Linzin 桌面時鐘 (Linzin Desk Clock)

[繁體中文](#-繁體中文) | [简体中文](#-简体中文) | [English](#-english) | [📸 實機展示 (Gallery)](#-實機展示--gallery)

<br/>

<img width="600" alt="展示動態" src="https://github.com/user-attachments/assets/a740dcbe-572d-4b59-98d2-ca08c627bbb3" />

</div>

<br/>

<div align="center">

<a href="https://github.com/linzin86/LinzinDeskClock/releases/">
  <img src="https://img.shields.io/badge/下載最新版本_APK-81C7D4?style=for-the-badge&logo=android&logoColor=5DAC81&labelColor=81C7D4" alt="Download APK" style="height: 64px !important; max-height: 64px !important;" height="64" />
</a>

</div>

---

## 繁體中文

- **專為大摺疊螢幕手機（Foldable）量身打造**。
- **帳篷模式自動觸發**：當手機處於半摺立起狀態時，透過轉軸感應器（Hinge Sensor）與 `cmd device_state` 指令，自動以外螢幕顯示各風格桌面時鐘與小工具。
- **靈感來源**：靈感來自 iPhone Duo，使用 Gemini 輔助製作。
- **直立機友好**：要是你手邊真的沒有大摺手機，好啦一般直立機也可以用。

### ✨ 核心特色

- **轉軸角度即時感知**：半摺（30° ~ 120°）自動切換外螢幕橫向時鐘，完全展開（> 150°）或閉合時自動復原。
- **數款時鐘錶盤設計**：多款精美錶盤風格，將來還會陸續新增更多。
- **多功能自適應版面**：支援 100% 全螢幕單區塊，或切換為雙區塊掌握迷你月曆、行事曆行程與即時天氣預報。
- **深色 OLED 防烙印保護**：內建棋盤式微幅抖動防烙印機制（Shader-based Anti-Burn-in），閒置 1 分鐘觸發，隨後每隔 5 分鐘切換亮暗像素。
- **輕量純粹、乾淨無負擔**：APP 本體僅約 5MB，零肥大體積、不濫用權限、終生免費、無廣告、無內購。

### 📱 相容性說明

- **一般時鐘**：最低 Android 5.0（API 21）。API 21 已驗證一般手機核心操作；這不代表所有功能或裝置皆已實測。
- **摺疊感應與外螢幕**：標準轉軸感應器需要 Android 11（API 30）及對應硬體；外螢幕切換另需 OEM 指令與權限支援，不能只憑品牌或系統版本保證成功。
- **小摺疊（Flip）與直立機**：進入 APP 點擊「不是大摺疊機？」按鈕，可手動放行體驗橫向全螢幕時鐘（可能需要你付出一些代價）。
- **其他能力限制**：挖孔邊界控制需要 Android 9（API 28）；RuntimeShader 防烙印需要 Android 13（API 33）。較舊系統保留一般時鐘，這些能力不適用。
- **定位與天氣**：Android 5–10（API 21–29）支援 LocationListener 主動定位，Android 11 起支援單次定位；仍需定位授權、可用定位來源與 HTTPS 連線，並非只能讀取位置快取。

---

## 简体中文

- **专为大折叠屏手机（Foldable）量身打造**。
- **帐篷模式自动触发**：当手机处于半折立起状态时，通过转轴传感器（Hinge Sensor）与 `cmd device_state` 指令，自动以外屏显示各风格桌面时钟与小组件。
- **灵感来源**：灵感来自 iPhone Duo，使用 Gemini 辅助制作。
- **直板机友好**：要是你手边真没有大折叠手机，好吧一般直板机也可以用。

### ✨ 核心特色

- **转轴角度即时感知**：半折（30° ~ 120°）自动切换外屏横向时钟，完全展开（> 150°）或闭合时自动复原。
- **数款时钟表盘设计**：多款精美表盘风格，后续还会陆续新增更多。
- **多功能自适应布局**：支持 100% 全屏单区块，或切换为双区块掌握迷你日历、日程安排与实时天气预报。
- **深色 OLED 防烧屏保护**：内置棋盘式微幅抖动防烧屏机制（Shader-based Anti-Burn-in），闲置 1 分钟触发，随后每隔 5 分钟切换亮暗像素。
- **轻量纯粹、干净无负担**：APP 本体仅约 5MB，体积轻巧、不滥用权限、终身免费、无广告、无内购。

### 📱 兼容性说明

- **普通时钟**：最低 Android 5.0（API 21）。API 21 已验证普通手机核心操作；这不代表所有功能或设备均已实测。
- **折叠感应与外屏**：标准铰链传感器需要 Android 11（API 30）及对应硬件；外屏切换另需 OEM 指令与权限支持，不能仅凭品牌或系统版本保证成功。
- **小折叠（Flip）与直板机**：进入 APP 点击“不是大折叠机？”按钮，可手动放行体验横屏桌面时钟（可能需要你付出一点代价）。
- **其他能力限制**：挖孔边界控制需要 Android 9（API 28）；RuntimeShader 防烧屏需要 Android 13（API 33）。较旧系统保留普通时钟，这些能力不适用。
- **定位与天气**：Android 5–10（API 21–29）支持 LocationListener 主动定位，Android 11 起支持单次定位；仍需定位权限、可用定位来源与 HTTPS 连接，并非只能读取位置缓存。

---

## English

- **Tailored for Large Foldable Smartphones**.
- **Tent Mode Auto-Trigger**: When the phone is semi-folded into tent/flex mode, it utilizes the Hinge Sensor and `cmd device_state` to automatically display stylish landscape clocks and widgets on the cover screen.
- **Inspiration**: Inspired by the iPhone Duo, developed with Gemini assistance.
- **Slab-Phone Friendly**: Don't have a foldable phone? Don't worry, standard slab phones can join the fun too.

### ✨ Key Features

- **Real-Time Hinge Awareness**: Automatically triggers landscape desk clock on the cover screen at 30°~120°, and restores the system display when unfolded (> 150°) or folded shut.
- **Multiple Watch Face Designs**: Various aesthetic watch face styles, with more coming in future updates.
- **Adaptive Split Layout**: Switch between full-screen single display or dual-panel layout with mini calendar, upcoming calendar events, and real-time weather.
- **OLED Anti-Burn-in Protection**: Shader-based checkerboard anti-burn-in jitter shifts pixels every 5 minutes during idle.
- **Lightweight & Clean**: APK size is only ~5MB, with zero bloat, no unnecessary permissions, no ads, and completely free.

### 📱 Compatibility

- **General clock**: Android 5.0 (API 21) or later. The ordinary-phone core flow has been verified on API 21; this does not establish every feature or device as tested.
- **Hinge and cover display**: Standard hinge sensing requires Android 11 (API 30) and suitable hardware. Cover-display switching also requires working OEM commands and permissions; brand or OS version alone does not guarantee support.
- **Flips & Slab Phones**: Tap the "Not a Foldable?" button on the initial screen to bypass and enjoy the full-screen desk clock.
- **Other capabilities**: Cutout control requires Android 9 (API 28); RuntimeShader burn-in protection requires Android 13 (API 33). General clock mode remains available on older systems, with these capabilities unavailable.
- **Location and weather**: Android 5–10 (API 21–29) can actively request location through LocationListener; Android 11 adds single-fix requests. Location permission, an available provider and HTTPS connectivity are still required; older versions are not limited to cached location.

---

## 📸 實機展示 / Gallery

<div align="center">

<img width="90%" alt="DSC08111" src="https://github.com/user-attachments/assets/22966365-dbd0-4f06-b227-a8ace36d6f84" />

<p align="center">
  <img width="48%" alt="錶盤樣式 1" src="https://github.com/user-attachments/assets/9c6ebf9b-8d4d-41ba-9ca1-4d14a703298f" />
  <img width="48%" alt="錶盤樣式 2" src="https://github.com/user-attachments/assets/43f4fb77-753e-45bf-bf79-d815b161c9e9" />
</p>

<p align="center">
  <img width="48%" alt="錶盤樣式 3" src="https://github.com/user-attachments/assets/def510e1-4516-45e3-afe8-b0b6a73be874" />
  <img width="48%" alt="雙區塊設定" src="https://github.com/user-attachments/assets/e5b7eab0-7326-469f-ab36-526f191b5fb2" />
</p>

</div>
