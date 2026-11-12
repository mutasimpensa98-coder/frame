# Next

Updated 2026-11-12, after the watchlist, cleanup suggestions, stats and automatic backup were built on branch `pack-library`. Before that, updated 2026-11-08, after launcher shortcuts, a Continue watching widget, the next episode date, a trailer button and "What should I watch tonight?" were built on branch `pack-home`. Read `STATUS.md` first. R1 applies as changed by the GitHub waiver in `WAIVERS.md`: one private repository on the owner's account, and nothing else.

The history must stay fit to publish. `.gitignore` and `tools/hooks/pre-commit`, set through `core.hooksPath`, keep out what the waiver bans. A new clone needs `git config core.hooksPath tools/hooks`.

Testing stays small: essentials only, and long runs such as the soak only when the owner asks.

## 1. State of the phone

The phone runs 1.0.104, which holds the owner-requests package and the TMDB and IMDb switches (`361e7b6`, version commit `db09ff3`). The owner tried it and sent three requests: the Settings icons are not coloured, the swipe to refresh should be more than a moving icon, and there is no online subtitle search. No answers to items 20 to 43 of section 2 are recorded yet.

The answer to those three requests is committed (`319d718`) with version 1.0.105 (`65b53b6`). Whether the phone runs 1.0.105 is not recorded here, and none of it has been seen on a phone.

The owner then asked for launcher shortcuts, a Continue watching widget, the next episode date and a trailer on Details, and a "What should I watch tonight?" pick. They are built and unit tested on branch `pack-home`, with schema 5, and not seen on any phone. `STATUS.md`, section "This round", lists them. Shipping them is step 1 of section 7.

The owner then asked for a watchlist «حابب نشوفه», cleanup suggestions in Media Manager, «إحصائياتي» and automatic backup. They are built and unit tested on branch `pack-library`, with schema 6, and not seen on any phone. `STATUS.md`, section "This round: watchlist, cleanup, stats and automatic backup", lists them. Owner checks are items 75 to 91 of section 2.

The release build is on the phone, updated in place from the first install, with the library, progress, settings and keys intact. `firstInstallTime` did not change.

Seen on the phone: the Settings pages, Media Manager sizes, themes, accents, language, long press, hide and restore, the player subtitle sheet and the blue TMDB badge.

Seen on the test copy, which is now removed: favourites and the private folder. Move in, locked after a forced stop, wrong password refused, playback with the name hidden, move out, and all 4 files came back byte-identical at their old paths.

Not seen yet:

1. Fingerprint unlock. It needs the owner's finger.
2. Delete to the phone's trash. The test clips were pushed by adb and have no MediaStore row, so the app called the delete permanent and it was cancelled. Try a real clip the owner does not need.
3. Online subtitle search. With no key, only series episodes can now be searched, through Gestdown. A film still needs a key.
4. The general swipe to refresh, from 1.0.103 on, and its new pill from this round. It scans, fetches art, text and ratings again for titles older than 10 minutes, retries titles with no match, and waits up to 8 s before the rest carries on in the background. The owner was using the phone, so it has not been seen.
5. The yellow IMDb badge. The general refresh should fetch ratings for titles matched before the OMDb key was added.

The 2 demo folders on the phone are already empty. The owner still has to delete `Movies/FrameGlassTest` from My Files, and empty `D:\FrameGlass\_to-delete`. Test clips on the PC went there, so a matrix run first needs `tools/make_testmedia.py`, `python tools/matrix.py push` and `.\fg.ps1 qa`.

## 2. Owner checklist for this round

Send this to the owner as it stands, and record the answers in `docs/evidence/phase-1/owner-answers.md`.

1. افتح التطبيق بعد التثبيت. هل بدأ يبحث وحده في التلفون بدون ما تضيف مجلد؟
2. هل ظهرت فيديوهاتك في الرئيسية؟ وهل مقاطع الكاميرا وواتساب في صف «مقاطع» لحالها؟
3. هل ظهرت الأفلام والمسلسلات ببطاقات بنفس الشكل في الرئيسية وفي المكتبة؟
4. شغّل فيديو دقيقة، ثم ارجع. هل ظهر شريط التتبع على بطاقته؟
5. أضف مفتاح TMDB من الإعدادات. هل ظهر التقييم والغلاف على الأفلام والمسلسلات؟
6. افتح «مقاطع» و«مجلدات» في المكتبة. هل كل شيء في مكانه؟
7. هل ظهر فيديو ما تبيه، مثل مقاطع تطبيقات أو ملفات مؤقتة؟ اكتب اسم المجلد.
8. كم تقريبًا أخذ البحث الأول حتى انتهى؟

جولة التحسينات. جرّب أول شيء بمقطع ما يهمك، مش بفيديو مهم:

9. من الإعدادات، غيّر المظهر بين داكن وفاتح وحسب التلفون، وجرّب الألوان الستة. هل كل النصوص واضحة في الفاتح؟ وهل المشغّل بقى داكن؟
10. غيّر لغة التطبيق بين العربي والإنجليزي. هل كل الشاشات تقلب صح؟
11. ضغطة طويلة على بطاقة: هل ظهرت القائمة كاملة؟ جرّب المفضلة، وإخفاء العنوان، واستبعاد المجلد.
12. افتح تفاصيل الملف. هل الحجم والمسار والدقة صحيحة؟
13. اسحب الشاشة لتحت في الرئيسية أو المكتبة. هل بدأ بحث جديد وظهر عدد الفيديوهات الجديدة؟
14. افتح إدارة الوسائط. هل أحجام المجلدات والفيديوهات قريبة من اللي تعرفه؟
15. احذف مقطع تجربة. هل وضّحت لك رسالة التأكيد إن كان يمشي لسلة المهملات أو ينحذف نهائيًا؟ وهل قدرت ترجّعه من السلة؟
16. افتح المجلد الخاص أول مرة. هل طلع رمز الاسترجاع؟ اكتبه في مكان آمن. وهل البصمة تفتح المجلد؟
17. انقل مقطع تجربة للمجلد الخاص، وشغّله، ثم رجّعه. هل رجع لمجلده الأصلي؟ وهل اختفى من الرئيسية والبحث وإدارة الوسائط وهو داخل المجلد؟
18. وأنت تشغّل فيديو من المجلد الخاص، حاول تاخذ لقطة شاشة، وشوف الإشعار. هل الاسم مخفي؟
19. في المشغّل، افتح الترجمة وابحث أونلاين (يحتاج مفتاح ترجمة). هل ظهرت النتائج والعربي أولها؟ وهل اشتغلت الترجمة بدون ما يعيد الفيديو؟

جولة طلباتك الأخيرة. هذي تنفع بعد ما تثبّت النسخة الجاية فوق 1.0.103:

20. أول فتحة بعد التحديث: هل مكتبتك ومكان وقوفك في كل فيديو والإعدادات والمفاتيح بقوا زي ما هم؟ التطبيق يجيب بيانات كل الأفلام والمسلسلات من جديد مرة وحدة، فكم تقريبًا أخذ لين هدأ؟ وهل سخن التلفون؟
21. من الإعدادات › المكتبة شغّل «وضع السينما». هل الرئيسية و«الكل» في المكتبة صاروا يعرضوا الأفلام والمسلسلات والأنمي بس؟ وهل بقية فيديوهاتك موجودة في «مقاطع»؟ طفّيه بعدها: هل رجع كل شيء زي قبل؟
22. اسحب بين صفحات الواجهة الكبيرة فوق في الرئيسية. هل كل صفحة فيها صورة، وما فيه ولا صفحة فاضية أو سوداء؟ وهل آخر فيديو كنت تتفرج عليه باين، في الواجهة أو في «تابع المشاهدة»؟
23. افتح تفاصيل مسلسل وانزل لتحت في الحلقات، في الداكن والفاتح وبالعربي. هل ظهر فوق شريط زجاجي فيه اسم المسلسل؟ وهل ما عاد فيه شيء يبان تحت شريط الساعة والبطارية؟
24. ضغطة طويلة على بطاقة، وبعدها زر الرجوع. هل القائمة تتقفل بس، بدون ما تطلع من الشاشة؟
25. في المظهر الفاتح افتح «حول». هل شعار التطبيق واضح؟
26. غيّر لغة التطبيق للعربي، وسكّر التطبيق وافتحه. هل أسماء الأفلام والتصنيفات والقصة صارت بالعربي، وين ما تكون متوفرة؟ وارجع للإنجليزي: هل رجعت؟
27. لو ما عندكش مفتاح OMDb، ضيفه من الإعدادات. هل ظهر تقييم IMDb الأصفر في صفحة التفاصيل بدون ما تسحب الشاشة للتحديث؟ (البطاقات تبقى على تقييم TMDB.)
28. مقاطعك الشخصية اللي في اسمها رقم بس، مثل «رحلة البحر - 3»: هل طلعت من «أنمي» وصارت في «مقاطع»؟ وهل فيه أنمي حقيقي طلع من «أنمي» بالغلط؟
29. شغّل فيلم بترجمة تعرف إن توقيتها غلط، واختارها من قائمة الترجمة. هل اتزامنت وحدها، وطلعت رسالة فيها الفرق بالثواني وزر «التراجع عن المزامنة»؟ كم أخذت تقريبًا؟ وهل الفيديو تقطّع وهي تشتغل، خصوصًا في فيلم 4K؟
30. جرّب «التراجع عن المزامنة». هل رجع التوقيت زي ما كان؟
31. حط على نفس الفيلم ترجمة فيلم ثاني. هل قال إنه مش متأكد، وما غيّر التوقيت؟
32. لو عندك ترجمة تمشي صح في الأول وتتأخر شوي شوي لين آخر الفيلم: هل صحّحها، وكتب إنه صحّح السرعة؟ سكّر الفيديو وافتحه: هل الترجمة الأصلية لسه في القائمة جنب النسخة المصحّحة؟
33. التأخير اليدوي للترجمة صار يوصل لدقيقة قدّام أو ورا. هل يشتغل زي قبل؟

طلباتك الثلاث على 1.0.104. جرّبها بعد النسخة الجاية، اللي تنزل فوق 1.0.104:

44. افتح الإعدادات. هل كل سطر في الصفحة الأولى جنبه مربع ملوّن، وكل واحد بلون غير الثاني، والرمز أبيض وواضح فيه؟ جرّب الداكن والفاتح والعربي. (الصفحات اللي داخلها ما فيهاش مربعات ملوّنة، وهذا مقصود.)
45. في الرئيسية أو المكتبة، اسحب الشاشة لتحت ببطء. هل ظهر فوق شريط زجاجي صغير ينزل مع صبعك ومكتوب فيه «اسحب للتحديث»؟ كمّل السحب: هل تبدّل لـ«أفلِت للتحديث» وحسّيت برجّة خفيفة في التلفون، والقائمة تمطّطت شوي؟
46. أفلِت. هل الشريط ورّاك المراحل وهي تمشي، مثل عدد الملفات اللي تفحّصت وبعدها تحديث التفاصيل بالعدد، مع خط رفيع يتعبّى؟ وفي الآخر، هل كتب النتيجة: كم فيديو جديد وكم عنوان تحدّث، أو «كل شيء محدَّث»؟ كم ثانية تقريبًا أخذ؟
47. والنتيجة لسه ظاهرة، اسحب مرة ثانية على طول. هل الشريط رجع يتبع صبعك ويقول «اسحب» ثم «أفلِت» مع الرجّة؟
48. من إعدادات التلفون › تسهيل الاستخدام، شغّل تقليل الحركة، وارجع اسحب. هل القائمة ما عادت تتمطّط، والشريط لسه يورّي المراحل؟
49. شغّل حلقة من مسلسل معروف، وافتح الترجمة. هل فيه سطر «البحث عبر الإنترنت» حتى بدون مفتاح ترجمة؟ اضغطه: هل جاب نتائج، وجنب كل نتيجة اسم الموقع؟ وتحت النتائج، هل فيه سطر صغير يقول وين بحث، وإن الموقع اللي بدون مفتاح للمسلسلات فقط؟ اكتب كم نتيجة عربية لقى، لأن العربي قليل في هذا الموقع.
50. اختار نتيجة. هل نزلت الترجمة واشتغلت على الحلقة، والتوقيت صح أو اتزامن وحده؟
51. شغّل فيلم، وابحث أونلاين بدون مفتاح. هل قال إن المواقع اللي بدون مفتاح فيها مسلسلات بس، وإن مفتاح مجاني من الإعدادات يجيب ترجمات الأفلام؟
52. طفّي «الميزات المتصلة» وافتح الترجمة. هل سطر البحث باهت ومكتوب إن ميزات الإنترنت متوقفة؟ وشغّل فيديو من المجلد الخاص: هل السطر باهت ومكتوب إنه غير متاح للمجلد الخاص؟
53. افتح الإعدادات › الميزات المتصلة والمفاتيح. هل مكتوب إن المسلسلات ما تحتاجش مفتاح؟

طلبك الجديد: الاختصارات والويدجت وموعد الحلقة الجاية والإعلان و«ماذا أشاهد الليلة؟». جرّبها بعد النسخة اللي فيها هذي الإضافات. التحديث يغيّر قاعدة البيانات (من 4 لـ5)، فأول شيء تأكد إن مكتبتك ومكان وقوفك والمفضلة بقوا زي ما هم:

54. اضغط ضغطة طويلة على أيقونة Frame Glass في شاشة التلفون. هل ظهر «تابع» واسم آخر شيء كنت تتفرج عليه، ومعاه لين اثنين ثانيين من «تابع المشاهدة»، وكل واحد بصورة غلافه؟ اضغط واحد: هل فتح المشغّل على طول من نفس المكان اللي وقفت فيه؟
55. تفرّج شوي على حلقة ثانية ووقّف، وارجع لشاشة التلفون واستنى حوالي 10 ثواني. هل الاختصارات تبدّلت، واللي تفرجت عليه آخر شيء صار الأول؟
56. ضيف ويدجت «تابع المشاهدة» (ضغطة طويلة على مكان فاضي › الأدوات › Frame Glass). هل شكلها داكن وشفاف شوي بحواف مدوّرة، وفيها لين 3 عناوين بالغلاف والاسم ورقم الحلقة وخط التقدّم؟ اضغط عنوان: هل فتح المشغّل من مكان وقوفك؟
57. قبل ما يكون عندك شيء تتابعه، أو بعد ما تكمّل كل شيء: هل الويدجت كاتبة «لا شيء تتابعه الآن» وتفتح التطبيق لما تضغطها؟
58. حرّك فيديو من «تابع المشاهدة» للمجلد الخاص. بعد شوي، هل اختفى من الاختصارات ومن الويدجت؟ وهل المقاطع الشخصية عمرها ما ظهرت فيهم؟
59. اسحب الشاشة للتحديث واستنى لين يكمّل. بعدها افتح تفاصيل مسلسل لسه يعرض وحلقته الجاية ما نزلتش. هل فيه سطر زي «الحلقة 6 من الموسم 2 تنزل الخميس 12 نوفمبر»، واليوم والشهر صح بالعربي؟ ولو نازلة اليوم أو بكرة، هل كتب «اليوم» أو «غدًا»؟
60. افتح مسلسل انتهى عرضه. هل السطر مش موجود؟
61. في التفاصيل، هل فيه زر «الإعلان»؟ اضغطه: هل فتح الإعلان في يوتيوب أو المتصفح؟ وهل جاب الإعلان العربي لو موجود، وإلا الإنجليزي؟ طفّي «الميزات المتصلة»: هل الزر اختفى؟
62. فيلم أو مسلسل تعرّف عليه IMDb وTMDB طافي: هل الصفحة سليمة، بدون سطر الحلقة الجاية وبدون زر الإعلان؟
63. مسلسل وصلت فيه لآخر حلقة عندك ولسه ما كمّلتهاش، والحلقة الجاية ما نزلتش: في «تابع المشاهدة» هل تحت اسمه مكتوب «حلقة جديدة يوم …» بدل اسم الحلقة؟ يبان مزحوم ولا مقبول؟
64. في المكتبة، اضغط زر النقاط الثلاث فوق › «ماذا أشاهد الليلة؟». هل طلع لك فيلم أو مسلسل ما بدأتهوش، بغلافه واسمه وسنته وتقييمه ومدته وقصة قصيرة؟ اضغط «غيره» كم مرة: هل كل مرة يجيب حاجة ثانية، وما يعاودش نفس العنوان ورا بعض؟
65. جرّب «فيلم» و«مسلسل» وتصنيف من التصنيفات. هل الاختيار يلتزم بيهم؟ وهل «شغّل» يشغّله على طول، و«تفاصيل» يفتح صفحته؟ وهل عمره طلع لك فيديو من المجلد الخاص أو مقطع شخصي؟

الترجمة التلقائية وسطر المواقع . جرّبها بعد التحديث اللي فيه هذا:

66. افتح الإعدادات › الترجمة. هل فيه سطر «ترجمة تلقائية من النت» واختياراته متوقفة، العربية، العربية ثم الإنجليزية، والمختار العربية؟
67. شغّل حلقة مسلسل معروف ما فيهاش ترجمة عربية، والميزات المتصلة شغّالة. خلّيها تشتغل وما تلمسش شي. هل بعد ثواني ظهرت ترجمة عربية وحدها والفيديو ما وقفش؟ وهل ظهرت فوق رسالة زجاجية صغيرة مثل «تم تحميل ترجمة عربية من Gestdown» أو SubDL، واختفت وحدها بعد شوي؟ كم ثانية أخذت؟
68. في نفس الرسالة، اضغط «غيّرها». هل فتحت نتائج البحث عبر الإنترنت، وتقدر تختار غيرها؟
69. سكّر الحلقة وافتحها مرة ثانية. هل الترجمة اللي نزلت لسه موجودة ومختارة، وما ظهرتش الرسالة مرة ثانية؟
70. شغّل فيديو فيه ترجمة عربية من قبل، مدمجة أو ملف جنبه. هل ما ظهرت أي رسالة، والترجمة اللي عندك بقت زي ما هي؟
71. شغّل مقطع من الكاميرا أو واتساب، وفيديو من المجلد الخاص. هل ما صار أي تحميل ولا رسالة؟
72. لو عندك مفتاح OpenSubtitles بس: شغّل فيلم ما فيهش ترجمة عربية. هل ما نزلت ترجمة وحدها؟ (OpenSubtitles يشتغل بس لما تضغط إنت، عشان ما يخلص حدّك اليومي.)
73. حط الاختيار على «العربية ثم الإنجليزية»، وشغّل حلقة جديدة ما لقيتلهاش ترجمة عربية. هل نزلت إنجليزية والرسالة تقول إنجليزية؟ وحطه على «متوقفة»: هل ما عاد ينزل شي وحده؟
74. افتح الترجمة › البحث عبر الإنترنت وبدون مفاتيح. تحت النتائج، هل السطر الصغير مكتوب فيه «أضف مفتاح SubDL مجاني للأفلام والترجمة العربية»؟ اضغطه: هل سكّر المشغّل وفتح الإعدادات › الميزات المتصلة والمفاتيح؟ وارجع للخلف: هل رجعت للمكان اللي كنت فيه قبل المشغّل؟ وهل لما تفتح الحلقة ثاني تكمل من نفس المكان؟

حابب نشوفه، اقتراحات التنظيف، إحصائياتي والنسخ التلقائي. جرّبها بعد التحديث اللي فيه هذا:

75. بعد التحديث: هل المكتبة والتقدّم والمفضلة والإعدادات والمفاتيح كلها زي ما هي؟ (في هذا التحديث قاعدة البيانات تنتقل للنسخة 6.)
76. اضغط مطوّلًا على بطاقة فيلم ما شفتهوش › «إضافة إلى «حابب نشوفه»». هل ظهر في الرئيسية صف «حابب نشوفه» بعد «تابع المشاهدة» مباشرة، حتى لو فيه عنوان واحد بس؟ وهل المفضلة ما تغيّرتش؟
77. في المكتبة، هل ظهرت شريحة «حابب نشوفه» وفيها العنوان؟ افتح صفحة مسلسل › زر النقاط الثلاث: هل فيه «إضافة إلى «حابب نشوفه»»، ولما تضغطه يتبدّل لـ«إزالة من «حابب نشوفه»»؟
78. ابدأ الفيلم اللي في القائمة وطلع منه في النص. هل انتقل لـ«تابع المشاهدة» وما عادش في صف «حابب نشوفه» في الرئيسية؟ بعدها كمّله للآخر: هل طلع من القائمة وحده، والشريحة اختفت لو كان آخر عنوان؟
79. اعمل نسخة احتياطية يدوية وفيها عنوان في «حابب نشوفه»، شيله من القائمة، ورجّع النسخة. هل رجع العنوان للقائمة؟
80. الإعدادات › إدارة الوسائط › شريحة «اقتراحات». هل فوق مكتوب كم يمكن توفير؟ وتحته «نسخ زائدة» لو عندك نفس الحلقة أو الفيلم بجودتين، و«شاهدتها، الأكبر أولًا»؟ هل الأحجام صح، وتحت كل نسخة زائدة مكتوب أي نسخة تبقى، وهي الأعلى جودة؟
81. هل عمره ظهر في الاقتراحات فيديو من المجلد الخاص، أو مقطع من الكاميرا أو واتساب؟
82. اختار من الاقتراحات فيديو واحد ما تحتاجهوش واضغط «حذف». هل طلعت نفس ورقة التأكيد بالعدد والحجم، ومكتوب إنه يروح لسلة المهملات؟ أكّد: هل تقدر ترجّعه من سلة المعرض؟ (ما تجرّبش «تحديد الكل» وفيه حاجات تحتاجها.)
83. المكتبة › زر النقاط الثلاث › «إحصائياتي». قبل ما تشوف أي شي بعد التحديث، هل الصفحة تقول «لا شيء بعد»؟ بعدها شوف حلقة حوالي 10 دقايق وارجع: هل «هذا الأسبوع» و«هذا الشهر» و«منذ البداية» حوالي 10 دقايق، وعمود اليوم في الرسم طلع؟
84. وإنت تتفرج، قدّم 20 دقيقة لقدّام بالشريط. هل الإحصائيات ما زادتش 20 دقيقة؟
85. هل المسلسل اللي شفته ظهر تحت «أكثر المسلسلات مشاهدة»؟ شوف دقايق من فيديو في المجلد الخاص: هل الساعات زادت واسمه ما ظهرش في أي مكان في الصفحة؟
86. في الرسم، هل أسماء الأيام بالعربي، والأقدم على اليمين واليوم على اليسار؟ وهل «هذا الأسبوع» يبدأ من يوم السبت؟
87. الإعدادات › النسخ الاحتياطي › «نسخ احتياطي تلقائي». اختار «كل يوم»: هل فتح لك اختيار المجلد؟ اعمل مجلد جديد (مثلًا Frame Glass داخل Documents) واختاره واسمح. هل اسمه ظهر في سطر «المجلد»؟
88. اضغط «انسخ الآن». هل ظهر «آخر نسخة:» بالتاريخ والساعة، وفي المجلد ملف اسمه زي frame-glass-auto-2026-11-12-230509.zip؟
89. حط في نفس المجلد نسخة يدوية، واضغط «انسخ الآن» 6 مرات. هل بقيت آخر 5 نسخ تلقائية بس، والنسخة اليدوية ما تمسّتش؟
90. بكرة، بدون ما تضغط شي والبطارية مش ضعيفة: هل فيه نسخة جديدة في المجلد؟ لو ما فيش، افتح التطبيق واستنى دقيقة: هل تنعمل وحدها؟
91. امسح المجلد من «ملفاتي»، وارجع اضغط «انسخ الآن». هل ظهر سطر فشل يقول إن المجلد غير موجود ولازم تختاره من جديد؟

مفاتيح TMDB وIMDb. جرّبها بعد نفس التحديث:

34. افتح الإعدادات › الميزات المتصلة والمفاتيح. هل فيه مفتاح تشغيل جنب TMDB وجنب «IMDb عبر OMDb»؟ طفّي واحد ورجّعه: هل المفتاح المحفوظ بقى، وما طلب منك تكتبه من جديد؟ وهل تحت كل واحد سطر يشرح شن يصير لما يطفى، بالعربي وبالإنجليزي؟
35. طفّي TMDB وخلّي IMDb شغّال. هل البطاقات والتفاصيل صارت تعرض تقييم IMDb الأصفر بس، وما عاد فيه تقييم TMDB الأزرق في أي مكان؟ وهل ظهر سطر يقول إن التعرّف على الفيديوهات الجديدة يجري عن طريق IMDb؟
36. وTMDB طافي، حط في التلفون فيلم جديد في اسمه السنة. هل تعرّف عليه التطبيق، وجاب الغلاف والقصة والتصنيفات وأسماء الممثلين والمدة وتقييم IMDb؟ النص يكون بالإنجليزي، وبدون خلفية عريضة وبدون صور ممثلين.
37. افتح نفس الفيلم في الواجهة الكبيرة فوق في الرئيسية، وفي صفحة التفاصيل. هل الغلاف ظاهر كامل فوق خلفية ناعمة من ألوانه، مش مقصوص ومش لقطة من الفيديو؟ جرّب الداكن والفاتح والعربي.
38. وTMDB طافي، حط مسلسل جديد. هل ظهرت أسماء الحلقات وتقييم IMDb جنب كل حلقة؟
39. افتح «تصحيح المطابقة» وTMDB طافي. هل النتائج جات من IMDb؟ ولو ما لقى شي، هل الرسالة تقول IMDb مش TMDB؟ بعدها طفّي «الميزات المتصلة» وافتحه مرة ثانية: هل الرسالة تقولك تشغّل الميزات المتصلة؟
40. لو عندك مكتبة كبيرة، طلبات OMDb المجانية تخلص بعد حوالي ألف في اليوم. هل التعرّف وقف وقتها بدون رسالة خطأ، وكمّل وحده بعد الساعة 2 الصبح تقريبًا بتوقيت ليبيا؟
41. رجّع TMDB شغّال. بعد دقايق، هل الأفلام اللي تعرّف عليها IMDb صار فيها خلفية وشعار وصور ممثلين من TMDB؟ وهل مكان وقوفك والمفضلة والأغلفة والمطابقات اللي اخترتها بإيدك بقوا زي ما هم؟ وهل تقييم IMDb تحت الحلقات اختفى؟
42. طفّي الاثنين. هل اختفت كل التقييمات، والمكتبة والتشغيل يشتغلوا عادي؟
43. لو عندك أنمي له أكثر من موسم وكل موسم باسم مختلف: وTMDB طافي، هل كل موسم بقى بطاقة لحاله؟

## 3. Watch for on the phone

These come from reading the code. None has been seen.

- **Time and heat of the first search.** Every new file is hashed, then probed with up to 3 at once. A phone with thousands of clips will run this for a while.
- **Playback during the first search.** The probing does not pause while a video plays, and playback has priority under section 7.9. If playback stutters, pause `Enricher` while the player is open.
- **All Files granted without leaving the app.** A catch-up starts only when `MainActivity` is stopped and started again. In split screen or a pop-up window it may not stop, and Home then says no videos until Search again is tapped.
- **Folder name case.** If MediaStore spells a folder with different case from the disk, its videos can get a second row, which shows as a second version.
- **Home changing during the first search.** The hero list follows the newest files, so it moves until the search ends.
- **Trash through MediaStore.** Direct `IS_TRASHED` is expected to work with All Files access. If Android refuses it, the system confirmation appears. A subtitle goes through the Files table, and `createTrashRequest` may refuse a batch that holds one. The whole batch then comes back as failed. Nothing is lost, but the delete would need a fix.
- **Time to move into the private folder.** Every file is copied, then the whole copy is read back and hashed, so a big film takes about twice its copy time, with heat.
- **Recovery after a kill during a move.** Both sides are hashed again in `beforeScan`, so one large file delays the first scan after that kill.
- **BiometricPrompt under R8.** The constructors of `BiometricFragment`, `FingerprintDialogFragment` and `BiometricViewModel` are in `seeds.txt`, kept by the fragment and lifecycle consumer rules. No extra keep rule was needed, but the prompt has only been built, never shown.
- **`MainActivity` is now a `FragmentActivity`.** Watch for anything that changed with it, such as back handling or the trash confirmation result.
- **Recents flag.** A process death during private playback leaves the flag set until the next public play.
- **Font scale 1.3.** `TopGlassBar` is fixed at 34 sp, so settings titles may clip.

From the owner-requests package, read in the code and never run:

- **The first open after the update.** Schema 2 to 3 runs, then the clip pass and the anime pass, then every matched title is fetched once more because `metaLanguage` is NULL on every row: details, 3 images, all seasons and one OMDb call each. With many titles this spends OMDb's 1,000 calls a day, and it runs while the owner looks at Home.
- **Arabic from TMDB.** Plain `ar` is asked for. Whether TMDB answers it in Arabic is unverified. `AppLanguage` reads `LocaleManager` first; whether the application context sees a change made in Settings has not been checked.
- **Automatic sync on real films.** A second decoder reads the audio while the film plays: decode speed, heat, the speech detector on real film audio, the thresholds and any stutter, mostly with EAC3 or Dolby audio under 4K, are unmeasured. The sync gives up after 25 s.
- **A frame rate fix** writes a stretched copy to `filesDir/player-subs/` and shows it live, because NextLib's subtitle speed cannot take a ratio.
- **Hero art** is decoded once for every feed Home gets. During the first search that can be every 400 ms or so, for up to 5 heroes.
- **The OMDb fetch after a new key** waits for a swipe when the network fails.

From the TMDB and IMDb switches, read in the code and never run:

- **OMDb's poster host.** Posters are taken to come from `m.media-amazon.com` `[UNVERIFIED]`. Any other host is blocked, so such a title shows its video frame instead.
- **OMDb's day.** It is taken to end at midnight UTC `[UNVERIFIED]`. Rows that need OMDb park until 00:10 UTC. If OMDb resets at another time, they either wait longer than needed or spend one call and park again.
- **Fix match after the limit.** Once OMDb's day is spent and TMDB is off, Fix match is empty until the next day, and its message says to try tomorrow.
- **The matching line on Home** counts every queued row, including rows parked until OMDb's next day, so it can stay up overnight.
- **Titles OMDb could not settle** are asked again at most once a week, on a swipe, through `Title.omdbTriedAt`. Fix match always asks.
- **Online features turned on** now also queues the TMDB return and the missing IMDb ratings. Turning Online off and on again fetches TMDB titles with no IMDb rating once more.
- **Anime matched through OMDb never merges** on the IMDb id, because every cour shares it. Two cours stay two cards until TMDB is back and the AniList check decides.
- **TMDB turned back on** costs one TMDB find call per OMDb title, then its full fetch.
- **Hero and Details show the poster** for every matched title with no backdrop, TMDB titles included, instead of a video frame. A manual cover on an unmatched title keeps the frame.

From this round's three requests, read in the code and never run:

- **Gestdown live.** Its replies come from fixtures written from live searches on 2026-11-08. No download has been tried live: downloads now send `Accept: */*` instead of JSON, in case the server negotiates. Status 423 for an unknown language is `[UNVERIFIED]`. If Gestdown starts asking for a key, keyless search for series stops, as Wyzie did in mpvEx.
- **A show is found by name.** The show's TMDB id picks among the answers. With no TMDB id, a name that matches two shows gives nothing, on purpose.
- **The pill's haptic** uses `SegmentTick`. Whether the S26 Ultra plays it as a light tick, and whether the stretch of 3.5 % looks like rubber or like a jump, needs the phone.
- **A pull while the box is still springing back** from the last refresh gives no "Release" and no tick until the box has come to rest, but letting go still refreshes.
- **The sites line under subtitle results** is text only. Nothing in `:app` implements `PlayerHost.subtitleKeysIntent()`, so it cannot open the keys page.

## 4. Known limits in this build

Watchlist, cleanup, stats and automatic backup (`pack-library`, not seen on a phone):

- The watch log starts with this build. Time watched before it is not counted, and the first save after the app starts only sets a mark, so up to one save interval (about 5 s) of each session is not counted.
- Time is counted in video time: at 2x speed, 5 minutes on the clock that play 10 minutes of the video count as 10 minutes. Faster than about 2x counts nothing.
- Stats are not in the backup, so a restore on a new phone starts them again.
- The week follows the phone's region. A phone set to Arabic with no region may start the week on a day other than Saturday.
- A periodic WorkManager job may not run while One UI keeps the app asleep. The app then backs up on the next open, in the background, when one is due.
- Automatic backup deletes only files named exactly like its own. If the file provider renames a new file, that file is never deleted, so it can pile up.
- An extra version is judged by height, then size, as F-L9 picks the default. Two copies of equal height keep the bigger one.

Shortcuts, widget, next episode, trailer and tonight's pick (this round, not seen on a phone):

- Shortcuts and the widget refresh about 8 s after the last progress save, file change or title change, and only while the app's process is alive. Otherwise the widget asks on the system period of 30 minutes. Android may rate-limit `setDynamicShortcuts` for an app in the background, which would leave the old shortcuts until the next time the app is in front.
- Titles already in the library have no next-episode date or trailer until a swipe to refresh fetches them again, or the 150-day refresh does. A swipe refetches every TMDB title fetched more than 10 minutes before, and every series whose announced date has passed.
- The TMDB request asks for videos in the app language, English and no language (`include_video_language`). A trailer only in a third language is not found.
- The Home hint shows only on the card of the newest episode the owner has. A series whose every owned episode is watched has no card, so it shows no hint.
- Widget covers and shortcut icons are decoded from the stored poster on each update. Their look on One UI is unseen.

- A cut MKV plays to the cut, then shows a friendly message. It cannot seek or resume, and a refused seek shows a message.
- A cut MKV with cues at the front, cut exactly between clusters, holds the last frame with no message.
- The delayed error that reaches the player for a cut file was read from bytecode only. Matrix row C2 is the real check.
- `ScanState.currentFolder` is filled in, but no screen shows it.
- Changing subtitle encoding or adding a subtitle file still reopens the video. Online results load live.
- Gestdown holds TV episodes only. A film, or a file with no episode number, needs a subtitle key, and Arabic on Gestdown was thin in the probes.
- Settings sub-pages have no colour tiles. Only the root page has them.
- HDR contrast under the 0.78 player sheet scrim is unmeasured.
- A kill between a copy reaching the private folder and its row committing leaves an unlisted file there. It costs space only, and the original stays. It is not swept on purpose: if the database were ever lost, a sweep would destroy the only copies.
- If the private copy is damaged and the original is already gone, nothing can restore it. The card stays in the folder.
- Automatic sync runs on a subtitle the owner picks, adds, downloads or taps "Sync with the speech" for. Sidecars loaded at open are not synced by themselves, and embedded tracks cannot be synced.
- Undo of a sync ends when the video reopens, for example after an encoding change or an added file. The original subtitle stays in the list, so choosing it again brings it back, and that starts a new sync with its own Undo.
- Every subtitle copied in for a video is listed when it opens, including old picks and retimed copies.
- Clips already matched as anime before this build stay anime until Fix match.
- A title matched through OMDb has no backdrop, title logo, original title, cast photo, character, episode still or collection, and its text is English. Its cast members have negative ids, so the person page lists them by name only.
- Only a series matched through OMDb has episode IMDb ratings. They are cleared once TMDB holds the title.
- Restoring a backup written before the switches keeps the phone's switches as they are.
- A cue longer than 15 s counts for nothing in sync.
- The ClipPass rewrite of stored tags and the anime pass need Room, so only `StoredParse.reread` is tested.
- `PrivateVaultImpl`, `MediaManagerImpl` and `MediaStoreTrash` have no test that runs them, because they need Room and MediaStore on a device. Only their pure parts are tested on the JVM.

## 5. Decisions for the owner

1. **Recently added on Home.** F-U5 lists Top rated, genre and collection only. With no key none of them can appear, so this round added Recently added as a fallback, used only when none of those rows qualifies. Keep it, or return to the brief's list?
2. **Player sheets.** They are solid now, because `SurfaceView` gives Haze nothing to sample. Image 08 shows dimmed video behind the sheet. Keep solid, go back to a see-through tint, or try the `PixelCopy` backdrop of section 7.5?
3. **Videos under 4.** Brief rule: a row with fewer than 4 titles is hidden. A phone with films and 1 to 3 clips shows those clips only in Library. Keep the rule for the Videos row?
4. **Cover tint.** `coverTint` is a Settings switch and defaults to off, which keeps the dark look as it was. On spreads the cover colour further. Keep it off by default?
5. **Lost recovery code.** The code is held in memory until the owner confirms it. If the app dies before that, the code is gone and only the password opens the folder, so a forgotten password then means the videos cannot be reached. A way to issue a new code while unlocked would close this, and it needs a new call beside `PrivateVault`. Build it?
6. **Uninstalling.** Private videos live in app storage, so uninstalling or clearing the app's data deletes them unless the owner picks Keep data. `hasFragileUserData` offers that choice, and the private folder shows a warning. This cannot be solved without storing them elsewhere. Accept it?
7. **Moving to a new phone.** `allowBackup="false"` stops cloud backup, but since Android 12 it does not stop device-to-device transfer, and lint flags this. With no `dataExtractionRules`, a transfer may copy `files/vault` and the database to the new phone. The keys are sealed to this phone, so the folder would not open there, but the files would have left it. Adding rules that exclude everything closes this, and the app's own backup zip then becomes the only way to move the library. Add them?

## 6. Gate 0, still open

Gate 0 does not block app work under the waiver, but it still has to pass. Done so far: matrix 50 of 53 Core and the delay proof, both in `phase-0-results.md`.

- Run C2 for the full clip. It stopped at 8 s, before the cut.
- S4 missed one seek by 1.1 frames. V11_vfr needs a check that fits a variable frame rate.
- `python tools/matrix.py soak 30` for the soak.
- Step 0.7, glass over video. `VideoBlurHook.ENABLED` stays false until this is measured. `D:\FrameGlass\releases\test\FG Test phase 0.apk` is the build with the blur button.
- Step 0.8, storage proof, needs a USB drive. Step 0.9, mpv, is skipped under D3.
- Real files for H4 to H6, A7, S9 and S10.
- Complete `phase-0-results.md` with what brief section 0.10 asks: one row per `[UNVERIFIED]` item and per engine risk, and a plain recommendation.
- Owner checks: font choice, HDR look, Arabic subtitle rendering, dialogue on A2 to A5.

## 7. Build next, in order

1. **Merge and ship `pack-library`.** Watchlist, cleanup suggestions, stats and automatic backup, with schema step 5 to 6 (one nullable `Title` column and the new `WatchLog` table), replayed by `tools/check_update.py` and never run on Android. Send items 75 to 91 of section 2. Then the steps below.
2. **Ship the shortcuts, widget, next episode, trailer, tonight's pick and the automatic subtitle.** Both branches are merged into `main`. Write `docs/RELEASE-NOTES.md` in Arabic, push `main`, run `.g.ps1 release`, and send the checklist of section 2, items 54 to 74, with items 20 to 53 if the owner has not answered them. This round has schema step 4 to 5: five nullable `Title` columns, replayed by `tools/check_update.py`, and never run on Android yet. Watch the first live automatic subtitle download: Gestdown and SubDL replies in the tests are fixtures.
3. **Sites line opens the keys page** is built in the same round (`PlayerHost.subtitleKeysIntent()` through `AppLinks`), item 74.
4. **F-T2 chapters and F-T3 frame step.** No code exists. Phase 7.
5. **F-S4 local intro detection.** Phase 7b.
6. **Tests that need the phone or a device runner:** `Scanner`, `CatchUp`, `ClipPass`, `MigrationTestHelper` for 1 to 2, 2 to 3, 3 to 4, 4 to 5 and 5 to 6, the metadata queue under the switches against a fake server, a move in and out with the app killed at each step, the trash flow, the Gate 2 forced decoder failure, and the Gate 5 clipped text walk.
7. **`:benchmark` module and Baseline Profile.** Neither exists, and Phase 4 and DoD-04 need them.

## 8. Missing project files

- `docs/VERSIONS.md`. `gradle/libs.versions.toml` has entries that section 4.2 does not list: `material-icons-extended` 1.7.8, `junit` 4.13.2, and `biometric` 1.1.0, which the private folder waiver allows. Log them with the reason, and record the `:app:dependencies` result that section 4.2 asks for.
- `docs/design-tokens.md` does not have the light theme or the accents yet.
- `tools/name_report.py`, section 6.2, and `tools/make_fixture_library.py`, Gate 1. Until that tool exists, demo clips are `ffmpeg -t 15 -i testmedia/gen/V3_hevc_8bit_1080p.mkv -c copy -metadata title=demo-<i>-<stem> <tmp>.mkv`, pushed to `/sdcard/Movies/FG Titles/<name>`. Names: The.Matrix.1999.1080p.BluRay.x264.mkv, Inception.2010.1080p.WEB-DL.x265.mkv, Interstellar.2014.1080p.BluRay.mkv, Dune.Part.Two.2024.2160p.WEB-DL.mkv, The.Dark.Knight.2008.1080p.mkv, Breaking Bad/Season 1/Breaking.Bad.S01E01.1080p.mkv, S01E02 and S01E03 in the same form, [SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv, and the same with 06.
- `.\fg.ps1 gate N` covers gate 0 only.
- The pre-commit hook of section 3.4 is not installed.
