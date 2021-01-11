# Appium Test Instrumentor

## Setup
- Install the libraries: `pip install -r requirements.txt`
- For each app, create a directory in `tests`. 
    - Each test case should be located in a file starts with `Test`.
    - Each file must contain only one test method (starts with `test_`)
        - Do not use `from A import B` and use `B` in the code. Instead `import A as NAME_A` and use `NAME_A.B`. For example, look at `WAIT_MODULE` in `tests/1-demo/TestLogin.py`.
- Once you're done, instrument the test files in a directory by running `python augmentor.py <test_dir>` where `<test_dir>` is the name of the added test directory (for example, `1-demo`).
- If everything goes well, for each test file `TestSomething.py`, you should see another file `aug_TestSomething.py` which is the instrumented file.
- Finally, start the emulator and  run the tests in `aug_TestSomething.py` files. If everything goes well you should see a json file `aug_TestSomething.json` which is the use case 
