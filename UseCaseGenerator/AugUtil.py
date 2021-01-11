import json
import logging

# local imports
import lxml.html
from lxml import etree

from WidgetUtil import WidgetUtil
from logger import logger

logger.setLevel(logging.DEBUG)


class AugUtil:
    @staticmethod
    def generate_event(widget, action, skipped, *action_args):
        event = {k: v for k, v in widget.items()}
        event["action"] = action
        event["skip"] = skipped
        if action_args:
            event["action_args"] = action_args
        return event

    @staticmethod
    def generate_sleep_event(skipped, sleep_time):
        event = {"sleep": sleep_time, "skip": skipped}
        return event

    @staticmethod
    def save_events(events, fname):
        # prefix = "D:/Dropbox/document/UCI-2016/manuscript/AccessiblityTesting/AccessibilityTestExecutor/TransDroid/"
        # fname = prefix + 'test-guidelines/'+ fname.split(".")[0][len("aug_Test_"):] + ".json"
        fname = 'test-guidelines/'+ fname.split(".")[0][len("aug_Test_"):] + ".json"
        print("FName " + fname)
        with open(fname, "w", encoding="utf-8") as f:
            json.dump(events, f, indent=2)

    # @staticmethod
    # def get_android_widget(dom, attr_name, attr_value, tag_name=''):
    #     if attr_name == EventAction.TEXT_NOT_PRESENT.value:
    #         w = {key: "" for key in WidgetUtil.FEATURE_KEYS + ["parent_text", "sibling_text"]}
    #         w["text"] = attr_value
    #         return w
    #     soup = BeautifulSoup(dom, 'lxml')
    #     if attr_name == EventAction.TEXT_PRESENT.value:
    #         cond = {'text': lambda x: x and attr_value in x}
    #     else:
    #         cond = {attr_name: attr_value}
    #     if tag_name:
    #         cond['class'] = tag_name
    #     ele = soup.find(attrs=cond)
    #     w = WidgetUtil.get_widget_from_soup_element(ele)
    #     if attr_name == EventAction.TEXT_PRESENT.value:
    #         w["text"] = attr_value
    #     return w

    @staticmethod
    def get_web_widget(dom, located_by, arg):
        located_by_label = ""
        w_attrs = ""
        if located_by == "id":
            located_by_label = "resourceId"
            w_attrs = WidgetUtil.get_attrs(dom, 'resource-id', arg)
        elif located_by == "accessibility_id":
            located_by_label = "contentDescription"
            w_attrs = WidgetUtil.get_attrs(dom, 'content-desc', arg)
        elif located_by == "xpath":
            located_by_label = "xpath"
            w_attrs = WidgetUtil.get_attrs(dom, 'xpath', arg)
        elif located_by == "text":
            located_by_label = "text"
        else:
            w_attrs = {x: "" for x in ["resource-id", "content-desc", "text", "xpath", "class"]}
            w_attrs["left_context"] = []
            w_attrs["right_context"] = []
        w_id, w_content_description, w_text, w_xpath, w_locator, w_class, w_left_context, w_right_context = \
            [w_attrs.get(x, "") for x in ["resource-id", "content-desc", "text", "xpath", "locator", "class", "left_context", "right_context"]]
        widget = {
            "resourceId": w_id,
            "contentDescription": w_content_description,
            "text": w_text,
            "class": w_class,
            "xpath": w_xpath,
            "locator": w_locator,
            "located_by": located_by_label,
            "left_context": w_left_context,
            "right_context": w_right_context,
            "skip": False,
        }
        return widget

    @staticmethod
    def get_stripped_strings(ele):
        # e.g., "Add New Post ‹ TransDroid — WordPress"
        strings = [s.split("‹")[0] for s in ele.stripped_strings]
        return strings
