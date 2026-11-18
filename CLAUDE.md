# Cragpack

Read README.md (structure, rules, commands) before changing code. Product decisions in docs/02-decisions.md are settled; don't reopen them without asking.

- Build UI only from `designsystem` components and tokens; compare against design/screens/{dark,sunlight}/*.png.
- The developer works on Windows with no Mac: verify Android locally; iOS is checked by CI's `ios` job.
- Check: `./gradlew :shared:testAndroidHostTest :shared:compileTestKotlinIosSimulatorArm64 :androidApp:assembleDebug` (the iOS task compiles iOS code on Windows; it catches JVM-only APIs like `toSortedMap`)
