# Визуальные материалы DriveTrace

## Промпт текущей обложки с салоном

```text
Use case: precise-object-edit / product-mockup.
Edit Image 1, the ORIGINAL DriveTrace car interior photograph. Image 2 is supporting reference for restraint only; do NOT use its empty studio setting.
User correction: KEEP THE CAR INTERIOR, but reduce distracting details.
Preserve the original composition: portrait phone mounted left and wide landscape head unit right, physically installed in a recognizable real car dashboard. Keep both screens large, readable and sharp. Faithfully preserve existing Russian app UI, values, simulation indicators and layout; do not redesign screens.
Simplify ONLY the surroundings: replace the vivid city skyline and sunset beyond the windshield with a very soft unfocused neutral dark grey exterior, no identifiable buildings, lights, bokeh or sunset. Retain the dashboard around and beneath the devices, but make it subdued matte dark graphite, low contrast with subtle realistic texture. Keep just a small cropped hint of steering wheel at far left and a simple narrow vent below to convey car context. Reduce chrome and strong blue reflections; remove the bright red hazard triangle from the crop and unnecessary buttons, illuminated instruments and shiny trim. Keep phone mount subtle. No colorful ambient light.
Lighting: soft neutral natural light, screens are the brightest elements; surrounding interior clearly visible but secondary. Believable automotive product photograph of a parked car, NOT detached floating devices, NOT studio background. Tight balanced 16:9 crop. No people, extra props, text outside screens, labels, logos or watermarks.
```

`drivetrace-hero-interior.png` — текущая обложка: салон автомобиля сохранён, город, закат, яркие блики и лишние детали убраны. Создана встроенным инструментом imagegen на основе первого мокапа.

`drivetrace-hero-minimal.png` — промежуточная минималистичная обложка: телефон и ГУ на тёмном фоне без деталей салона. Создана встроенным инструментом imagegen путём редактирования первого мокапа.

`drivetrace-hero.png` — первоначальный рекламный мокап приложения на Android-телефоне и головном устройстве автомобиля. Создан встроенным инструментом imagegen на основе настоящего скриншота `screenshots/17-live-dashboard.png`.

Это сгенерированная визуализация, а не фотография работы приложения в реальном автомобиле и не скриншот Desktop Head Unit. Компоновка автомобильного интерфейса приблизительная; набор показаний и действий соответствует экспериментальному экрану в `app/src/debug/java/dev/drivetrace/car/TelemetryCarService.kt`. На обоих экранах показаны тестовые данные симулятора. Android Auto доступен только в debug-сборке.

Рядом с обложкой при публикации сохраняйте подпись о генерации и экспериментальном статусе Android Auto. Оригинальные скриншоты приложения находятся в `screenshots/`.

## Промпт первого варианта

```text
Use case: product-mockup
Asset type: wide photographic promotional hero for the Russian DriveTrace Android app README, landscape 16:9.
Primary request: tasteful, photorealistic premium automotive product photograph showing DriveTrace on an Android phone and on the head unit of a parked car at dusk. This is a clearly disclosed promotional mockup, not evidence of deployment.
Input images: Image 1 is the actual app screenshot, supporting compositing input for the phone screen; preserve the exact Russian UI, typography, values, colors, and layout from this reference as much as possible. Do not redesign the phone app.
Scene: tight crop of a modern unbranded car center dashboard with a wide landscape head unit, a portrait Android phone in a clean mount to its left. No people, no driving action, no prominent car manufacturer logos. Dark graphite dashboard, restrained cool blue accents, soft evening light. Screens are sharp and legible with minimal glare; practical physical proportions. Phone and head unit dominate composition, full devices visible.
Phone: display the supplied screenshot faithfully including visible simulation status.
Head unit: faithfully depict a SIMPLE Android Auto list/pane screen, NOT a dashboard gauge interface. Dark flat background with header 'DriveTrace', three left aligned rows with secondary text:
row 1 title 'Скорость: 112 км/ч', secondary 'Обороты: 3584 об/мин'
row 2 title 'ОЖ: 28 °C', secondary 'Напряжение: 14.2 В'
row 3 title 'Идёт запись · 00:02', secondary 'Симуляция · тестовые данные'
bottom action button 'Сохранить поездку'.
A narrow standard Android Auto navigation rail at the side is acceptable. No map or charts on the head unit.
Style: sophisticated natural product photography, realistic textured dashboard, accurate screen perspectives, subtle screen illumination. Restrained and credible. No added headline, slogans, badges, decorative text, watermarks, or fake brand logos outside screens.
```

## Промпт варианта без салона

```text
Use case: precise-object-edit / product-mockup.
Edit the supplied DriveTrace promotional image into a much cleaner, minimal README hero.
Preserve the two devices and their existing screen contents faithfully: portrait Android phone on the left, landscape automotive head unit on the right. Preserve the Russian text, displayed values, simulation labels, colors, and all UI layouts. Screens must remain legible, crisp, and the main focus.
Remove ALL surroundings and distractions: entire car interior, steering wheel, dashboard, vents, hazard button, phone mount, city skyline, sunset, bokeh, textures, reflections and scenery.
Replace with a smooth solid dark graphite studio background (#101318). Only the phone and head unit remain, with understated slim black device frames, subtle soft grounding shadows and very restrained lighting on edges. No props, no added text, no decorative lines, no flares, no gradients with bright colors.
Composition: balanced clean 16:9 landscape, full devices visible with generous equal outer margins and clear gap between the devices. Near front-on perspective. The head unit is horizontally wide; phone is portrait and about the same visual height. Both screens large enough to read, centered as a group. Elegant restrained product presentation. Do not add any new UI or promotional labels.
```
