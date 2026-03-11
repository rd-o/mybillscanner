# My bill scanner

Build debug APK with
```bash
./gradlew assembledebug
```

Generate test bills
```bash
python generate_test_bills.py > bills.tex
pdflatex bills.tex 

```
