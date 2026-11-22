# راهنمای یادگیری Contract Testing Toolkit

این راهنما بر پایهٔ نسخهٔ `0.1.0` است. در پایان می‌توانید یک قرارداد مصرف‌کننده بنویسید، دو تعریف را آفلاین مقایسه کنید و همان انتظارها را روی پاسخ یک ارائه‌دهندهٔ HTTP محلی آزمایش کنید.

## مسئله‌ای که حل می‌کنیم

فرض کنید یک فروشگاه برای نمایش کالا از `GET /items` استفاده می‌کند. مصرف‌کننده انتظار دارد `id` رشته و `price` عدد باشد. پاسخ `200 OK` به‌تنهایی این انتظارها را تضمین نمی‌کند. اگر ارائه‌دهنده `id` را به عدد تبدیل کند و `price` را حذف کند، درخواست در سطح HTTP موفق است ولی دادهٔ لازم برای مصرف‌کننده فراهم نشده است.

ابزار دو مسیر مکمل دارد. مقایسهٔ آفلاین بررسی می‌کند که قرارداد پیشنهادی ارائه‌دهنده وعدهٔ لازم را داده باشد. تأیید HTTP به ارائه‌دهندهٔ محلی درخواست می‌فرستد و بررسی می‌کند که یک پاسخ مشاهده‌شده با قرارداد مصرف‌کننده سازگار باشد. وقتی تیم ارائه‌دهنده API را تغییر می‌دهد، هر دو بررسی می‌توانند پیش از ادغام تغییر در یک گردش‌کار توسعه اجرا شوند.

## قرارداد را بخوانید

فایل `fixtures/consumer-http.tsv` یک تعامل را در قالب TSV نسخهٔ ۱ تعریف می‌کند. میان ستون‌ها Tab واقعی قرار دارد.

```text
version	1
interaction	GET	/items	200
field	id	string
field	price	number
optional	currency	string
```

- `field` حضور مقدار را الزامی می‌کند. `id` باید از نوع رشته باشد و `price` مقدار عددی داشته باشد.
- `optional` اجازه می‌دهد فیلد حذف شود. اگر `currency` حاضر باشد، باید رشته باشد. `null` صریح با نبودن فیلد فرق دارد.
- عدد صحیحی مانند `12` شرط `number` را برآورده می‌کند. عددی مانند `12.5` یا عدد با نماد نمایی شرط `number` را برآورده می‌کند، ولی برای `integer` پذیرفته نمی‌شود.
- فیلد اضافهٔ `label` می‌تواند وارد پاسخ شود. چون قرارداد نیازی به آن ندارد، خودِ اضافه شدن آن سازگاری را بر هم نمی‌زند.
- نوع‌های `object` و `array` فقط نوع مقدار سطح بالا را بررسی می‌کنند. ابزار نحو JSON را می‌خواند و برای اعضای داخلی قانون نوع جداگانه اعمال نمی‌کند.

مشخصات فنی، نوع‌های پشتیبانی‌شده، ساختار فایل و محدودیت‌های مسیر را ثبت می‌کند. [مشخصات قرارداد](SPEC.md)

## ابزار را بسازید

یک JDK نسخهٔ ۱۷ یا جدیدتر و PowerShell لازم است. تعریف CI نسخهٔ `25.0.5+8` از Eclipse Temurin را انتخاب می‌کند و `javac --release 17` کد را برای Java 17 می‌سازد. اسکریپت آزمون از `-JavaHome`، متغیر `JAVA_HOME`، JDK پرتابل داخل پروژه یا PATH استفاده می‌کند. نسخه‌های پین‌شدهٔ Jackson Core و JUnit Console در صورت نیاز از Maven Central دریافت می‌شوند و قبل از استفاده SHA-256 آن‌ها بررسی می‌شود. در اولین اجرا، دریافت این وابستگی‌ها ممکن است به شبکه نیاز داشته باشد. اجرای خود دمو با وابستگی‌های آماده فقط از loopback استفاده می‌کند. [دریافت Temurin](https://adoptium.net/temurin/releases/)

از ریشهٔ مخزن اجرا کنید.

```powershell
pwsh -NoProfile -File scripts/verify.ps1
```

در Windows PowerShell 5.1 از `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1` استفاده کنید. کلاس‌ها و گزارش XML آزمون‌ها در یکی از پوشه‌های `bin/verification-*/` قرار می‌گیرند. برای دستورهای بعد، فرض بر این است که `java` در PATH است. اگر JDK را با `-JavaHome` انتخاب کردید، همان `bin/java` را برای اجرای CLI به کار ببرید.

```powershell
$run = Get-ChildItem -LiteralPath bin -Directory -Filter 'verification-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$classes = Join-Path $run.FullName 'classes'
$jackson = (Resolve-Path -LiteralPath 'bin/tools/jackson-core-2.21.8.jar').Path
$classpath = $classes + [IO.Path]::PathSeparator + $jackson
```

## تغییر سازگار و ناسازگار را مقایسه کنید

```powershell
java -cp $classpath toolkit.Main fixtures/consumer.tsv fixtures/provider-compatible.tsv
$LASTEXITCODE
java -cp $classpath toolkit.Main fixtures/consumer.tsv fixtures/provider-breaking.tsv
$LASTEXITCODE
```

نمونهٔ اول وضعیت `0` دارد. دلیل قبول آن این است که ارائه‌دهنده `price` را با نوع `integer` تضمین می‌کند و می‌تواند `label` را اضافه کند. نمونهٔ دوم وضعیت `1` دارد. ارائه‌دهندهٔ پیشنهادی نوع `id` را عوض کرده و `price` را از تضمین‌های خود حذف کرده است. اگر ارائه‌دهنده یک فیلد الزامی مصرف‌کننده را اختیاری اعلام کند، تضمین حضور آن از بین رفته و مقایسه آن را گزارش می‌کند.

## پاسخ واقعی یک ارائه‌دهندهٔ محلی را بررسی کنید

```powershell
java -cp $classpath toolkit.Main --demo fixtures/consumer-http.tsv
$LASTEXITCODE
```

دمو در هر مرحله یک نمونهٔ جداگانه از ارائه‌دهندهٔ ساختگی را روی یک درگاه آزاد `127.0.0.1` اجرا می‌کند. هر چهار پاسخ وضعیت `200` دارند.

| مرحله | بدنهٔ JSON | نتیجهٔ مورد انتظار |
| --- | --- | --- |
| `Baseline` | `{"id":"sku-42","price":12}` | سازگار |
| `Breaking` | `{"id":42}` | ناسازگار، به‌علت نوع شناسه و نبود قیمت |
| `Additive` | `{"id":"sku-42","price":12,"currency":"EUR","label":"Desk lamp"}` | سازگار |
| `Recovery` | `{"id":"sku-42","price":12}` | سازگار |

خروجی دمو با همین fixture به این صورت است.

```text
Baseline: compatible (expected)
Breaking: incompatible (expected)
  field 'id' type mismatch: expected STRING, got INTEGER
  field 'price' is missing
Additive: compatible (expected)
Recovery: compatible (expected)
```

دمو زمانی با وضعیت `0` تمام می‌شود که هر مرحله رفتار مورد انتظار را نشان دهد، از جمله ناسازگاری مرحلهٔ `Breaking`. برای مشاهدهٔ نتیجهٔ یک ارائه‌دهندهٔ مستقل، `java -cp $classpath toolkit.SampleProvider breaking` را در یک پوسته اجرا کنید. نشانی چاپ‌شده را در پوستهٔ دوم با همان classpath وارد کنید.

```powershell
$origin = Read-Host 'Paste the loopback HTTP origin printed by SampleProvider'
java -cp $classpath toolkit.Main --http fixtures/consumer-http.tsv $origin
$LASTEXITCODE
```

این نمونه با وضعیت `1` به پایان می‌رسد و دو اختلاف را در stdout می‌نویسد. ارائه‌دهنده را با Ctrl+C متوقف کنید. اگر CLI نتواند به درگاه محلی وصل شود، fixture نامعتبر باشد یا آدرس شکل مجاز را نداشته باشد، وضعیت `2` و پیام stderr دریافت می‌کنید. وضعیت `1` برای پاسخ ناسازگار است و می‌تواند از بدنهٔ نامعتبر، نوع محتوای نادرست یا وضعیت HTTP غیرمنتظره هم ناشی شود.

## معماری و مسیر ساخت

۱. `Contract` فایل TSV را می‌خواند، نسخه و سطرها را اعتبارسنجی می‌کند و فیلدهای الزامی و اختیاری را جدا نگه می‌دارد. قراردادهای قدیمی با سطرهای `field` همچنان خوانده می‌شوند.

۲. `Compatibility` مقایسهٔ نوع‌ها، تشخیص فیلد حذف‌شده و ترتیب گزارش اختلاف‌ها را متمرکز می‌کند. شاخهٔ آفلاین از این کلاس برای بررسی دو تعریف استفاده می‌کند.

۳. `HttpVerification` درخواست `GET` را با `HttpURLConnection` به IPv4 loopback می‌فرستد. redirect و proxy غیرفعال‌اند. محدودیت اتصال و انتظار خواندن هر کدام سه ثانیه است و حداکثر اندازهٔ بدنهٔ پذیرفته‌شده ۶۵٬۵۳۶ بایت است. Jackson Core نحو JSON را بررسی می‌کند و نوع فیلدهای سطح بالا را برای `Compatibility` آماده می‌کند.

۴. `SampleProvider` با `HttpServer` خود JDK پاسخ ساختگی را از مسیر `/items` ارائه می‌دهد. binding روی `127.0.0.1` و انتخاب درگاه توسط سیستم‌عامل باعث می‌شود برای دمو نیازی به آماده‌سازی سرویس دیگر نباشد.

۵. `Main` آرگومان‌ها را به یکی از مسیرهای مقایسه، HTTP یا دمو می‌فرستد و ناسازگاری قرارداد را از خطای ورودی و ارتباط تفکیک می‌کند. [تصمیم دربارهٔ مقایسهٔ آفلاین](adr/0001-offline-schema-fixtures.md) · [تصمیم دربارهٔ HTTP محلی](adr/0002-local-http-verification.md)

## شواهد آزمون و مرز بررسی

آزمون‌های JUnit ورودی‌های TSV، سازگاری نوع‌ها، اختیاری بودن فیلد و تفاوت `null` و نبودن فیلد را بررسی می‌کنند. آزمون‌های HTTP با یک سرور loopback اجرا می‌شوند و علاوه بر چهار حالت دمو، وضعیت HTTP نادرست، بدنهٔ معیوب، نوع محتوای نامناسب، بدنهٔ بزرگ و جلوگیری از دنبال کردن redirect را پوشش می‌دهند. گزارش XML و خروجی `observed-demo.txt` در `bin/verification-*/reports/` برای بازبینی نتیجه‌ها موجودند. [شواهد انتشار](RELEASE_EVIDENCE.md)

این نسخه یک تعامل و یک پاسخ JSON سطح بالا را در هر اجرا می‌سنجد. اعتبارسنجی ساختار تو‌در‌تو، درخواست‌های دارای بدنه، احراز هویت، سرور مستقر و فرمت‌های Pact و OpenAPI خارج از دامنه هستند. ایدهٔ قرارداد مصرف‌کننده‌محور از Pact الهام گرفته شده و قرارداد و گردش‌کار این پروژه مستقل طراحی شده‌اند. [مستندات رسمی Pact](https://docs.pact.io/getting_started/how_pact_works)

به‌عنوان تمرین، یک کپی از fixture مصرف‌کننده برای خود بسازید و `currency` را به فیلد الزامی تبدیل کنید. حالت `baseline` و سپس `additive` را با حالت `--http` آزمایش کنید. در حالت اول فیلد غایب است و در حالت دوم مقدار رشته‌ای دارد.
