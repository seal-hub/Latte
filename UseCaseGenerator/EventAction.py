import enum


class EventAction(enum.Enum):
    CLICK = "click"
    SEND_KEYS = "send_keys"
    CLEAR = "clear"
    JUMP_WITH_URL = "jump_with_url"  # only used in webdriver tests; converted to CLICK when translating to .json
    TEXT_PRESENT = "text_present"
    IS_DISPLAYED = "is_displayed"
    TEXT_NOT_PRESENT = "text_not_present"
    MOUSEOVER = "move_to_element"
    SLEEP = "sleep"


ORACLE_EVENT_ACTIONS = {
    EventAction.TEXT_PRESENT.value,
    EventAction.IS_DISPLAYED.value
}