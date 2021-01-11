import ast
import astor
from os import listdir
import sys
from os.path import isfile, join, exists, basename, dirname
from shutil import copy
import json
import logging
from pathlib import Path

# local imports
from EventAction import EventAction
from const import ANDROID_TEST_FOLDER, WEB_TEST_FOLDER, CONFIG_FOLDER, AUG_PREFIX
from logger import logger


logger.setLevel(logging.DEBUG)


class TestTransformer(ast.NodeTransformer):
    # Custom transformer
    def transform_imports(self, tree):
        imports = dict()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):  # e.g., import uniitest
                imports[node.names[0].name] = []
            if isinstance(node, ast.ImportFrom):  # e.g., from selenium import webdriver
                imports[node.module] = [n.name for n in node.names]
        to_add = {
            "os.path": ["basename"],
            "AugUtil": ["AugUtil"]
        }
        for k, v in to_add.items():
            if k not in imports:
                if v:
                    import_node = ast.ImportFrom(module=k, names=[ast.alias(name=n, asname=None) for n in v], level=0)
                else:
                    import_node = ast.Import(names=[ast.alias(name=k, asname=None)])
                tree.body.insert(0, import_node)

    # Overwritten transformers
    def visit_FunctionDef(self, node):
        if node.name == "setUp":
            logger.debug("setUp(): Initialize the event list")
            assign_node = ast.Assign(targets=[ast.Name(id="self.events", ctx=ast.Load())],
                                     value=ast.Name(id="[]", ctx=ast.Load()))
            node.body.insert(0, assign_node)
        elif node.name == "tearDown":
            logger.debug("tearDown(): Save the event list")
            call_node = ast.Call(
                func=ast.Attribute(value=ast.Name(id="AugUtil", ctx=ast.Load()), attr="save_events", ctx=ast.Load()),
                args=[ast.Name(id="self.events", ctx=ast.Load()), ast.Name(id="basename(__file__)", ctx=ast.Load())],
                keywords=[])
            expr_node = ast.Expr(value=call_node)
            node.body.insert(0, expr_node)
        elif node.name.startswith("initial") or node.name.startswith("actual"):
            logger.debug(f"{node.name}: Instrumentation")
            node = self.instrument(node, skipped=node.name.startswith("initial"))
        return node

    def instrument(self, node, skipped):
        # parse the original body
        supported_action = {a.value: a for a in EventAction}
        assigned_vars = dict()
        to_insert = []  # (idx, action, locator, arg, action_args)
        for i, n in enumerate(node.body):
            if isinstance(n, ast.Assign):
                # e.g., ele = self.driver.find_element_by_id("wp-admin-bar-site-name")
                if isinstance(n.targets[0], ast.Name) and isinstance(n.value, ast.Call):
                    target = n.targets[0].id  # ele
                    method = n.value.func.attr  # find_element_by_id
                    if method.startswith("find_element_by"):
                        arg_s = n.value.args[0].s  # wp-admin-bar-site-name
                        logger.debug(f"{target}, {method}, {arg_s}")
                        assigned_vars[target] = {
                            "method": method,
                            "arg_s": arg_s,
                            "idx": i
                        }
            elif isinstance(n, ast.Expr):
                if type(n.value) == ast.Call:
                    if isinstance(n.value.func.value, ast.Attribute):  # e.g., self.driver.get(URL)
                        caller = n.value.func.value.value.id  # self
                        attr = n.value.func.value.attr  # driver
                        method = n.value.func.attr  # get
                        f_url = ""  # could be an f-string
                        for v in n.value.args[0].values:
                            if isinstance(v, ast.Str):
                                f_url += v.s
                            elif isinstance(v, ast.FormattedValue):
                                f_url += "{" + v.value.id + "}"
                        logger.debug(f"{caller}.{attr}.{method}, {f_url}")
                        to_insert.append((i + 1, EventAction.JUMP_WITH_URL, f_url, "", ""))
                        continue
                    # e.g., ActionChains(self.driver).move_to_element(ele).perform()
                    if isinstance(n.value.func.value, ast.Call):
                        if isinstance(n.value.func.value.func, ast.Attribute):
                            method = n.value.func.value.func.attr
                            if method == EventAction.MOUSEOVER.value:
                                ele_moved_to = n.value.func.value.args[0].id
                                to_insert.append((i, EventAction.MOUSEOVER, assigned_vars[ele_moved_to]["method"],
                                                  assigned_vars[ele_moved_to]["arg_s"], ""))
                                logger.debug(f"{method}.{ele_moved_to}")
                                continue
                    caller = n.value.func.value.id
                    method = n.value.func.attr
                    logger.debug(f"{caller}, {method}")
                    if caller == 'time' and method == 'sleep':
                        action_args = [n.value.args[0].n]
                        to_insert.append((i, EventAction.SLEEP, "", "", action_args))
                    if caller in assigned_vars and method in supported_action:  # e.g., ele.click();
                        if assigned_vars[caller]["method"].startswith("find_element_by"):
                            action_args = [n.value.args[0].s] if method == EventAction.SEND_KEYS.value else []
                            to_insert.append((i, supported_action[method], assigned_vars[caller]["method"],
                                              assigned_vars[caller]["arg_s"], action_args))
                    elif caller == "self" and method == "assertTrue" and n.value.args:
                        # e.g., self.assertTrue("cross_transfer" in self.driver.page_source)
                        if type(n.value.args[0]) == ast.Compare:
                            arg_0 = n.value.args[0]
                            if isinstance(arg_0.left, ast.Str) and isinstance(arg_0.ops[0], ast.In) \
                                    and isinstance(arg_0.comparators[0], ast.Attribute) \
                                    and arg_0.comparators[0].attr == "page_source":
                                text = arg_0.left.s
                                logger.debug(f"{EventAction.TEXT_PRESENT.value}, {text}")
                                to_insert.append((i + 1, EventAction.TEXT_PRESENT, "", text, ""))
                            elif isinstance(arg_0.left, ast.Str) and isinstance(arg_0.ops[0], ast.NotIn) \
                                    and isinstance(arg_0.comparators[0], ast.Attribute) \
                                    and arg_0.comparators[0].attr == "page_source":
                                text = arg_0.left.s
                                logger.debug(f"{EventAction.TEXT_NOT_PRESENT.value}, {text}")
                                to_insert.append((i + 1, EventAction.TEXT_NOT_PRESENT, "", text, ""))
                        # e.g., elf.assertTrue(ele.is_displayed())
                        elif type(n.value.args[0]) == ast.Call:
                            arg_0 = n.value.args[0]
                            arg_method = arg_0.func.attr
                            arg_caller = arg_0.func.value.id
                            if arg_method == EventAction.IS_DISPLAYED.value and arg_caller in assigned_vars:
                                logger.debug(
                                    f"{EventAction.IS_DISPLAYED.value}, {assigned_vars[arg_caller]['method']}, {assigned_vars[arg_caller]['arg_s']}")
                                to_insert.append((i + 1, EventAction.IS_DISPLAYED, assigned_vars[arg_caller]["method"],
                                                  assigned_vars[arg_caller]["arg_s"], ""))
            logger.debug("--")
        # instrumentation starts
        logger.debug(to_insert)
        new_body = node.body[:]
        # # initialize the event list
        # assign_node = ast.Assign(targets=[ast.Name(id="self.events", ctx=ast.Load())],
        #                          value=ast.Name(id="[]", ctx=ast.Load()))
        # new_body.insert(0, assign_node)
        # inserted_lines = 1
        inserted_lines = 0
        for idx, action, locator, arg, action_args in to_insert:
            if action in {EventAction.CLICK, EventAction.SEND_KEYS, EventAction.MOUSEOVER, EventAction.CLEAR} \
                    and locator.startswith("find_element_by"):
                located_by = "_".join(locator.split("_")[3:])
            elif action == EventAction.IS_DISPLAYED and locator.startswith("find_element_by"):
                located_by = "_".join(locator.split("_")[3:])
            elif action == EventAction.TEXT_PRESENT:
                located_by = EventAction.TEXT_PRESENT.value
            elif action == EventAction.JUMP_WITH_URL:
                located_by = EventAction.JUMP_WITH_URL.value
            elif action == EventAction.TEXT_NOT_PRESENT:
                located_by = EventAction.TEXT_NOT_PRESENT.value
            elif action == EventAction.SLEEP:
                located_by = EventAction.SLEEP.value
            else:
                assert False, "located_by is unknown"

            if action == EventAction.SLEEP:
                # line 2 for event: (widget, action)
                args = [ast.NameConstant(skipped)]
                if action_args:
                    args += [ast.Name(id=f'"{act_arg}"', ctx=ast.Load()) for act_arg in action_args]
                call_node = ast.Call(
                    func=ast.Attribute(value=ast.Name(id="AugUtil", ctx=ast.Load()), attr="generate_sleep_event",
                                       ctx=ast.Load()),
                    args=args, keywords=[])
                assign_node = ast.Assign(targets=[ast.Name(id="aug_event", ctx=ast.Load())], value=call_node)
                new_body.insert(idx + inserted_lines, assign_node)

                # line 3 to append the generated event
                call_node = ast.Call(
                    func=ast.Attribute(value=ast.Name(id="self.events", ctx=ast.Load()), attr="append", ctx=ast.Load()),
                    args=[ast.Name(id="aug_event", ctx=ast.Load())],
                    keywords=[])
                expr_node = ast.Expr(value=call_node)
                new_body.insert(idx + 1 + inserted_lines, expr_node)
                inserted_lines += 2
            else:

                # line 1 for widget
                call_node = ast.Call(
                    func=ast.Attribute(value=ast.Name(id="AugUtil", ctx=ast.Load()), attr="get_web_widget",
                                       ctx=ast.Load()),
                    args=[ast.Name(id="self.driver.page_source", ctx=ast.Load()),
                          ast.Name(id=f'"{located_by}"', ctx=ast.Load()),
                          ast.Name(id=f'"{arg}"', ctx=ast.Load())],
                    keywords=[])
                assign_node = ast.Assign(targets=[ast.Name(id="aug_widget", ctx=ast.Load())],
                                         value=call_node)
                new_body.insert(idx + inserted_lines, assign_node)

                # line 2 for event: (widget, action)
                args = [ast.Name(id="aug_widget", ctx=ast.Load()),
                        ast.Name(id=f'"{action.value}"', ctx=ast.Load()),
                        ast.NameConstant(skipped)]
                if action_args:
                    args += [ast.Name(id=f'"{act_arg}"', ctx=ast.Load()) for act_arg in action_args]
                call_node = ast.Call(
                    func=ast.Attribute(value=ast.Name(id="AugUtil", ctx=ast.Load()), attr="generate_event",
                                       ctx=ast.Load()),
                    args=args, keywords=[])
                assign_node = ast.Assign(targets=[ast.Name(id="aug_event", ctx=ast.Load())], value=call_node)
                new_body.insert(idx + 1 + inserted_lines, assign_node)

                # line 3 to append the generated event
                call_node = ast.Call(
                    func=ast.Attribute(value=ast.Name(id="self.events", ctx=ast.Load()), attr="append", ctx=ast.Load()),
                    args=[ast.Name(id="aug_event", ctx=ast.Load())],
                    keywords=[])
                expr_node = ast.Expr(value=call_node)
                new_body.insert(idx + 2 + inserted_lines, expr_node)
                inserted_lines += 3
        # save the events
        # call_node = ast.Call(
        #     func=ast.Attribute(value=ast.Name(id="AugUtil", ctx=ast.Load()), attr="save_events", ctx=ast.Load()),
        #     args=[ast.Name(id="self.events", ctx=ast.Load()), ast.Name(id="basename(__file__)", ctx=ast.Load())],
        #     keywords=[])
        # expr_node = ast.Expr(value=call_node)
        # new_body.insert(len(new_body), expr_node)
        node.body = new_body
        return node


def make_config():
    Path(join(CONFIG_FOLDER, app)).mkdir(parents=True, exist_ok=True)
    if not exists(join(CONFIG_FOLDER, app, "config.json")):
        config = {
            "resource_path": "../../NavGraph/app/apktool-output/UNKNOWN",
            "model_path": "../../NavGraph/app/model/UNKNOWN",
            "launch_setting": {
                "default": [
                    "DEFAULT_APP_PACKAGE",
                    "DEFAULT_ACTIVITY_NAME"
                ]
            },
            "transfer_setting": {}
        }
        with open(join(CONFIG_FOLDER, app, "config.json"), "w+", encoding="utf-8") as f:
            json.dump(config, f, indent=2)


def is_reset(app, test_name):
    is_reset = True
    if app in {"wordpress", "owncloud"}:
        is_reset = False
    elif app in {"etsy"}:
        if test_name.split(".")[0] in {"TestSearch", "TestSearchDetail", "TestProfile", "TestHelp", "TestMessage",
                                       "TestOrder", "TestFavorite"}:
            is_reset = False
    return is_reset


if __name__ == "__main__":
    # if len(sys.argv) < 2:
    #     print("Please provide the name of test directory, e.g., '1-demo`")
    #     sys.exit()
    app = "2-geek"
    # app = sys.argv[1]
    test_folder = join("finalized-tests", app)
    tests = [f for f in listdir(test_folder) if isfile(join(test_folder, f)) and f.startswith("Test")]
    for t in tests:
        if app == "groupon" and "TestSignUp" in t:
            continue
        logger.debug(t)
        tree = astor.parse_file(join(test_folder, t))
        transformer = TestTransformer()
        transformer.transform_imports(tree)
        transformer.visit(tree)
        ast.fix_missing_locations(tree)
        with open(join(test_folder, AUG_PREFIX + t), "w", encoding="utf-8") as f:
            f.write(astor.to_source(tree))
        make_config()
        with open(join(CONFIG_FOLDER, app, "config.json"), "r+", encoding="utf-8") as f:
            config = json.load(f)
            config["transfer_setting"][AUG_PREFIX + t.split(".")[0]] = {
                "web_test_path": join(test_folder, AUG_PREFIX + t).replace("\\", "/"),
                "android_test_path": join(ANDROID_TEST_FOLDER, app, "generated").replace("\\", "/"),
                "use_stopwords": True,
                "expand_btn_to_text": False,
                "reset_data": is_reset(app, t),
            }
            f.seek(0)
            json.dump(config, f, indent=2)
            f.truncate()

    # copy("AugUtil.py", test_folder)
