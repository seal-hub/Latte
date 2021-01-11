from bs4 import BeautifulSoup
import xml.etree.ElementTree as ET
import re
# local import
from StrUtil import StrUtil
from lxml import etree


class WidgetUtil:
    # NAF means "Not Accessibility Friendly", e.g., a back button without any textual info like content-desc
    FEATURE_KEYS = ['class', 'resource-id', 'text', 'content-desc', 'clickable', 'password', 'naf']
    WIDGET_CLASSES = ['android.widget.EditText', 'android.widget.MultiAutoCompleteTextView', 'android.widget.TextView',
                      'android.widget.Button', 'android.widget.ImageButton', 'android.view.View']
    state_to_widgets = {}  # for a gui state, there are "all_widgets": a list of all widgets, and
                           # "most_similar_widgets": a dict for a source widget and the list of its most similar widgets and scores

    @staticmethod
    def get_gui_signature(xml_dom, pkg_name, act_name):
        """Get the signature for a GUI state by the package/activity name and the xml hierarchy
        Breadth first traversal for the non-leaf/leaf nodes and their cumulative index sequences
        """
        xml_dom = re.sub(r'&#\d+;', "", xml_dom)  # remove emoji
        root = ET.fromstring(xml_dom)
        queue = [(root, '0')]
        layouts = []
        executable_leaves = []
        while queue:
            node, idx = queue.pop()
            if len(list(node)):  # the node has child(ren)
                layouts.append(idx)
                for i, child in enumerate(node):
                    queue.insert(0, (child, idx + '-' + str(i)))
            else:  # a leaf node
                executable_leaves.append(idx)
        # print(layouts)
        # print(executable_leaves)
        sign = [pkg_name, act_name, '+'.join(layouts), '+'.join(executable_leaves)]
        return '!'.join(sign)

    @classmethod
    def get_widget_signature(cls, w):
        """Get the signature for a GUI widget by its attributes"""
        sign = []
        for k in cls.FEATURE_KEYS + ['package', 'activity']:
            if k in w:
                sign.append(w[k])
            else:
                sign.append('')
        return '!'.join(sign)

    @classmethod
    def get_most_similar_widget_from_cache(cls, gui_signature, widget_signature):
        """Return the most similar widget and the score to the source widget in a gui state in cache"""
        if gui_signature not in cls.state_to_widgets:
            return None, -1
        if 'most_similar_widgets' in cls.state_to_widgets[gui_signature]:
            if widget_signature in cls.state_to_widgets[gui_signature]['most_similar_widgets']:
                # print('Cache HIT from SIMILAR')
                return cls.state_to_widgets[gui_signature]['most_similar_widgets'][widget_signature][0]
        return None, -1

    @classmethod
    def get_all_widgets_from_cache(cls, gui_signature):
        """Return all widgets in a gui state in cache"""
        if gui_signature in cls.state_to_widgets and 'all_widgets' in cls.state_to_widgets[gui_signature]:
            # print('Cache HIT from ALL')
            return cls.state_to_widgets[gui_signature]['all_widgets']
        else:
            return None

    @staticmethod
    def get_parent_text(soup_ele):
        # consider immediate parent node's text if exists
        parent_text = ''
        parent = soup_ele.find_parent()
        if parent and 'text' in parent.attrs and parent['text']:
            parent_text += parent['text']
        # consider grandparent if it's TextInputLayout (for ? related apps)
        parent = parent.find_parent()
        if parent and 'text' in parent.attrs and parent['text'] and parent['class'][0] == 'TextInputLayout':
            parent_text += parent['text']
        return parent_text

    @staticmethod
    def get_sibling_text(soup_ele):
        # TODO
        return ""
        # (for Tip related apps)
        # consider immediate previous sibling text if exists and the parent is LinearLayout
        # sibling_text = ''
        # parent = soup_ele.find_parent()
        # if parent and parent['class'][0] in ['android.widget.LinearLayout', 'android.widget.RelativeLayout']:
        #     prev_sib = soup_ele.previous_sibling
        #     if prev_sib and 'text' in prev_sib.attrs and prev_sib['text']:
        #         sibling_text = prev_sib['text']
        # return sibling_text

    @classmethod
    def get_attrs(cls, dom, attr_name, attr_value, tag_name=''):
        soup = BeautifulSoup(dom, 'lxml')
        # print("Soup ", soup.prettify())
        # if attr_name == 'text-contain':
        #     cond = {'text': lambda x: x and attr_value in x}
        # else:
        #     cond = {attr_name: attr_value}
        # if tag_name:
        #     cond['class'] = tag_name
        query = ""
        if attr_name == 'resource-id':
            query = f"//node()[@resource-id='{attr_value}']"
        elif attr_name == "content-desc":
            query = f"//node()[@content-desc='{attr_value}']"
        elif attr_name == 'xpath':
            query = attr_value
        else:
            print("NOT IMPLEMENTED " + attr_name)
            return None
        # if attr_name == 'xpath':
        dom_utf8 = dom.encode('utf-8')
        parser = etree.XMLParser(ns_clean=True, recover=True, encoding='utf-8')
        tree = etree.fromstring(dom_utf8, parser)
        contexts_info = []
        ele = tree.xpath(query)[0]
        # TODO: Refactor
        ele_index = -1
        for i,x in enumerate(tree.getiterator()):
            x_attrs = dict(x.attrib.items())
            if len(x.getchildren()) == 0:
                if x_attrs.get('displayed', 'true') == 'false':
                    continue
                info = {'class': x_attrs.get('class', ''), 'text': x_attrs.get('text', ''), 'contentDescription': x_attrs.get('content-desc', ''),
                     'resourceId': x_attrs.get('resource-id', '')}
                if ele == x:
                    ele_index = len(contexts_info)
                contexts_info.append(info)
                # print(f"({x_attrs.get('class','')}-{x_attrs.get('text','')}-{x_attrs.get('content-desc','')})")
        left_context = []
        right_context = []
        CONTEXT_SIZE = 3
        if ele_index >= 0:
            for i in range(ele_index - 1, max(0, ele_index - CONTEXT_SIZE) - 1, -1):
                # if 'Toast' in contexts_info[i]['class']:
                #     continue
                left_context.append(contexts_info[i])
            for i in range(ele_index + 1, min(len(contexts_info), ele_index + 1 + CONTEXT_SIZE)):
                # if 'Toast' in contexts_info[i]['class']:
                #     continue
                right_context.append(contexts_info[i])

        # print("\n\n-------------Ele", ele, "\n\n")
        d = {}
        d['locator'] = attr_value
        d['xpath'] = cls.get_xpath(ele)
        d['left_context'] = left_context
        d['right_context'] = right_context
        attrs = dict(ele.attrib.items())
        for key in cls.FEATURE_KEYS:
            d[key] = attrs[key] if key in attrs else ""
            # TODO: I don't know what these are
            # if key == 'class':
            #     d[key] = d[key][0]  # for now, only consider the first class
            # elif key == 'clickable' and key in attrs and attrs[key] == 'false':
            #     d[key] = WidgetUtil.propagate_clickable(ele)
        # d['parent_text'] = WidgetUtil.get_parent_text(ele)
        # d['sibling_text'] = WidgetUtil.get_sibling_text(ele)
        if d['class'].endswith('Layout'):
            if d['text'] == "" and d['content-desc'] == "":
                res = cls.get_first_text(ele)
                if res is not None:
                    d['text'] = res
                else:
                    res = cls.get_first_cd(ele)
                    d['content-desc'] = res
        return d


    @classmethod
    def get_first_text(cls, node):
        attrs = dict(node.attrib.items())
        if attrs['text'] != "":
            return attrs['text']
        for ch in node.getchildren():
            res = cls.get_first_text(ch)
            if res is not None:
                return res
        return None

    @classmethod
    def get_first_cd(cls, node):
        attrs = dict(node.attrib.items())
        if attrs['content-desc'] != "":
            return attrs['content-desc']
        for ch in node.getchildren():
            res = cls.get_first_cd(ch)
            if res is not None:
                return res
        return None

    # TODO: It's for retrieving xpath from bs elements
    @classmethod
    def get_element(cls, node):
        # for XPATH we have to count only for nodes with same type!
        length = 0
        index = -1
        if node.getparent() is not None:
            for x in node.getparent().getchildren():
                if node.attrib.get('class', 'NONE1') == x.attrib.get('class', 'NONE2'):
                    length += 1
                if x == node:
                    index = length
        if length > 1:
            return f"{node.attrib.get('class', 'NONE')}[{index}]"
        return node.attrib.get('class', 'NONE')

    @classmethod
    def get_xpath(cls, node):
        path = '/' + cls.get_element(node)
        if node.getparent() is not None and node.getparent().attrib.get('class', 'NONE') != 'hierarchy':
            path = cls.get_xpath(node.getparent()) +path
        return path

    @classmethod
    def get_empty_attrs(cls):
        d = {}
        for key in cls.FEATURE_KEYS:
            d[key] = ""
        d['parent_text'] = ""
        d['sibling_text'] = ""
        return d

    @classmethod
    def find_all_widgets(cls, dom, pkg, act, target_pkg, update_cache=True):
        if 'com.android.launcher' in pkg:  # the app is closed
            return []

        if pkg != target_pkg:  # exclude all widgets not belonging to the app's package
            return []

        if act.startswith('com.facebook'):  # the app reaches facebook login, out of the app's scope
            return []

        gui_signature = WidgetUtil.get_gui_signature(dom, pkg, act)
        if not update_cache:
            widgets = WidgetUtil.get_all_widgets_from_cache(gui_signature)
            if widgets:
                return widgets

        soup = BeautifulSoup(dom, 'lxml')
        widgets = []
        for w_class in cls.WIDGET_CLASSES:
            elements = soup.find_all('', w_class)
            for e in elements:
                d = cls.get_widget_from_soup_element(e)
                if d:
                    if 'yelp' in gui_signature and 'text' in d and d['text'] == 'Sign up with Google':
                        d['text'] = 'SIGN UP WITH GOOGLE'  # Specific for Yelp
                    d['package'], d['activity'] = pkg, act
                    widgets.append(d)

        if widgets or update_cache:
            cls.state_to_widgets[gui_signature] = {'all_widgets': widgets, 'most_similar_widgets': {}}
        return widgets

    @classmethod
    def get_widget_from_soup_element(cls, e):
        if not e:
            return None
        d = {}
        if 'enabled' in e.attrs and e['enabled'] == 'true':
            for key in cls.FEATURE_KEYS:
                d[key] = e.attrs[key] if key in e.attrs else ''
                if key == 'class':
                    d[key] = d[key][0]  # for now, only consider the first class
                elif key == 'clickable' and key in e.attrs and e.attrs[key] == 'false':
                    d[key] = WidgetUtil.propagate_clickable(e)
                elif key == 'resource-id':
                    rid = d[key].split('/')[-1]
                    prefix = ''.join(d[key].split('/')[:-1])
                    d[key] = rid
                    d['id-prefix'] = prefix + '/' if prefix else ''
            d['parent_text'] = WidgetUtil.get_parent_text(e)
            d['sibling_text'] = WidgetUtil.get_sibling_text(e)
            return d
        else:
            return None

    @classmethod
    def propagate_clickable(cls, soup_element):
        # clickable propagation from immediate parent
        parent = soup_element.find_parent()
        if 'clickable' in parent.attrs and parent['clickable'] == 'true':
            return 'true'
        for i in range(2):  # a22-a23-b22 (mutated)
            parent = parent.find_parent()
            if parent and 'class' in parent.attrs and parent['class'][0] in ['android.widget.ListView']:
                if 'clickable' in parent.attrs and parent['clickable'] == 'true':
                    return 'true'
        return 'false'

    # @classmethod
    # def most_similar_widget(cls, current_widgets, src_event, dom, pkg, act,
    #                         use_stopwords=True, expand_btn_to_text=False, cross_check=False,
    #                         extra_score=0, update_cache=True):
    #     # if target_action['class'] == 'SYS_EVENT':
    #     #     return {'class': 'SYS_EVENT'}, 1
    #     gui_signature = WidgetUtil.get_gui_signature(dom, pkg, act)
    #     src_widget_signature = WidgetUtil.get_widget_signature(src_event)
    #     if not update_cache:
    #         w_res, score = WidgetUtil.get_most_similar_widget_from_cache(gui_signature, src_widget_signature)
    #         if w_res:
    #             return w_res, score
    #
    #     src_class = src_event['class']
    #     is_clickable = src_event['clickable']  # string
    #     is_password = src_event['password']  # string
    #     pairs = []
    #     if src_class in ['android.widget.ImageButton', 'android.widget.Button']:
    #         if expand_btn_to_text:
    #             src_class = ['android.widget.ImageButton', 'android.widget.Button', 'android.widget.TextView']
    #         else:
    #             src_class = ['android.widget.ImageButton', 'android.widget.Button']
    #     elif src_class == 'android.widget.TextView' and is_clickable == 'true':
    #         src_class = ['android.widget.ImageButton', 'android.widget.Button', 'android.widget.TextView']
    #     elif src_class == 'android.widget.EditText' and src_event['action'][0].startswith('wait_until'):
    #         # for tip apps, e.g., a51-a53-b51
    #         src_class = ['android.widget.EditText', 'android.widget.TextView']
    #     else:
    #         src_class = [src_class]
    #     for w in current_widgets:
    #         if w['class'] in src_class and w['password'] == is_password:
    #             if w['clickable'] == is_clickable:
    #                 score = WidgetUtil.weighted_sim(w, src_event, use_stopwords=use_stopwords, cross_check=cross_check)
    #                 # corner case for the password field in Target app registration
    #                 if not score and is_password == 'true' and not (w['text'] or w['resource-id'] or w['content-desc']) \
    #                         and w['class'] == 'android.widget.EditText':
    #                     score = 1.0
    #             elif src_event['action'][0].startswith('wait_until') and w['class'] in ['android.widget.EditText', 'android.widget.TextView']:
    #                 # if checking oracle, no need to check clickable consistence for TextView and EditText
    #                 # (for tip apps, e.g., a51-a51-b51 and a51-a53-b51)
    #                 score = WidgetUtil.weighted_sim(w, src_event, use_stopwords=use_stopwords, cross_check=cross_check)
    #             elif src_event['action'][0].startswith('swipe') and w['class'] in ['android.widget.TextView']:
    #                 # corner case for a21-a25-b22; no need to check clickable consistence for TextView and swipe action
    #                 score = WidgetUtil.weighted_sim(w, src_event, use_stopwords=use_stopwords, cross_check=cross_check)
    #             else:
    #                 score = None
    #             if score:
    #                 score = min(1.0, score + extra_score)
    #                 pairs.append((w, score))
    #
    #     pairs = sorted(pairs, key=lambda x: x[1], reverse=True)
    #     if pairs:
    #         '''
    #         for p in pairs:
    #             print(p[0], p[1])
    #         input('DUMP')
    #         '''
    #         candidate_pairs = [p for p in pairs if p[1] == pairs[0][1]]
    #         ans = WidgetUtil.advanced_select(candidate_pairs, src_event)
    #         assert gui_signature in cls.state_to_widgets
    #         # assert src_widget_signature not in cls.state_to_widgets[gui_signature]['most_similar_widgets']
    #         cls.state_to_widgets[gui_signature]['most_similar_widgets'][src_widget_signature] = [ans]
    #         return ans[0], ans[1]
    #     if update_cache:
    #         assert gui_signature in cls.state_to_widgets
    #         cls.state_to_widgets[gui_signature]['most_similar_widgets'][src_widget_signature] = [(None, -1)]
    #     return None, -1

    @staticmethod
    def advanced_select(sim_pairs, src_event):
        # If there are 1+ widgets with the same sim score,
        # pick the one with the most words in common with the target text
        if len(sim_pairs) == 1:
            return sim_pairs[0]
        ans = sim_pairs[0]
        src_text = src_event['text']
        if src_text:
            overlap_score = -1
            for p in sim_pairs:
                w_text = p[0]['text']
                if w_text:
                    s1 = set(src_text.lower().strip().split())
                    s2 = set(w_text.lower().strip().split())
                    intersection = len(s1.intersection(s2))
                    if intersection > overlap_score:
                        overlap_score = intersection
                        ans = p
        return ans


    @classmethod
    def is_equal(cls, w1, w2, ignore_activity=False):
        if not w1 or not w2:
            return False
        keys_for_equality = set(cls.FEATURE_KEYS)
        keys_for_equality.remove('naf')
        if not ignore_activity:
            keys_for_equality = keys_for_equality.union({'package', 'activity'})
        for k in keys_for_equality:
            if (k in w1 and k not in w2) or (k not in w1 and k in w2):
                return False
            if k in w1 and k in w2:
                v1, v2 = w1[k], w2[k]
                if k == 'resource-id' and 'id-prefix' in w1:
                    v1 = w1['id-prefix'] + w1[k]
                if k == 'resource-id' and 'id-prefix' in w2:
                    v2 = w2['id-prefix'] + w2[k]
                if v1 != v2:
                    return False
        return True

    '''
    @staticmethod
    def get_state_feature(act_name, dom, use_stopwords=True):
        """Express a GUI state as:
        1. tokenized words of current Activity;
        2. # of clickable elements / android.widget.EditText widget / long-clickable elements
        """
        soup = BeautifulSoup(dom, 'lxml')
        feature_vec = [
            len(soup.find_all(attrs={'clickable': 'true', 'enabled': 'true'})),
            len(soup.find_all(attrs={'class': 'android.widget.EditText', 'enabled': 'true'})),
            len(soup.find_all(attrs={'long-clickable': 'true', 'enabled': 'true'}))
        ]
        #denominator = sum(feature_vec)
        #if denominator > 0:
        #    feature_vec = [n/denominator for n in feature_vec]
        return {'act_name': str_util.tokenize('Activity', act_name, use_stopwords=use_stopwords), 'feature': feature_vec}
    '''

    @classmethod
    def locate_widget(cls, dom, criteria):
        # e.g., criteria =
        #   {'class': 'TextView', 'text': 'Okay', 'resource-id': 'tv_task', 'content-desc': ""} or
        #   {'class': 'android.widget.Button', 'resource-id': 'org.secuso.privacyfriendlytodolist:id/btn_skip'}
        regex_cria = {}
        for k, v in criteria.items():
            if v:
                v = v.replace('+', r'\+')  # for error when match special char '+'
                v = v.replace('?', r'\?')  # for error when match special char '?'
                # regex_cria[k] = re.compile(f'{v}$')
                regex_cria[k] = re.compile(f'{v}')
        if not regex_cria:
            return None
        soup = BeautifulSoup(dom, 'lxml')
        return cls.get_widget_from_soup_element(soup.find('', regex_cria))

    @classmethod
    def most_similar(cls, src_event, widgets, use_stopwords=True, expand_btn_to_text=False, cross_check=False):
        src_class = src_event['class']
        is_clickable = src_event['clickable']  # string
        is_password = src_event['password']  # string
        similars = []
        # if src_class in ['android.widget.ImageButton', 'android.widget.Button']:
        #     if expand_btn_to_text:
        #         src_class = ['android.widget.ImageButton', 'android.widget.Button', 'android.widget.TextView']
        #     else:
        #         src_class = ['android.widget.ImageButton', 'android.widget.Button']
        # elif src_class == 'android.widget.TextView' and is_clickable == 'true':
        #     src_class = ['android.widget.ImageButton', 'android.widget.Button', 'android.widget.TextView']
        #     if re.search(r'https://\w+\.\w+', src_event['text']):  # e.g., a15-a1x-b12
        #         src_class.append('android.widget.EditText')
        # elif src_class == 'android.widget.TextView' and src_event['action'][0].startswith('wait_until_text_presence'):
        #     # e.g., a53-a51-b51, a53-a52-b51
        #     src_class = ['android.widget.TextView', 'android.widget.EditText']
        # elif src_class == 'android.widget.EditText' and src_event['action'][0].startswith('wait_until_text_presence'):
        #     # e.g., a51-a53-b51
        #     src_class = ['android.widget.EditText', 'android.widget.TextView']
        # elif src_class == 'android.widget.EditText' and re.search(r'https://\w+\.\w+', src_event['text']):
        #     # e.g., a11-a15-b12
        #     src_class = ['android.widget.EditText', 'android.widget.TextView']
        # elif src_class in ['android.widget.MultiAutoCompleteTextView', 'android.widget.TextView']:  # a41, a42 (b42)
        #     src_class = ['android.widget.MultiAutoCompleteTextView', 'android.widget.TextView']
        #     if 'send_keys' in src_event['action']:
        #         src_class.append('android.widget.EditText')
        # else:
        #     src_class = [src_class]
        tgt_classes = [src_class]
        if src_class in ['android.widget.ImageButton', 'android.widget.Button']:
            tgt_classes = ['android.widget.ImageButton', 'android.widget.Button']
            if expand_btn_to_text:
                tgt_classes.append('android.widget.TextView')
        elif src_class == 'android.widget.TextView':
            if is_clickable == 'true':
                tgt_classes += ['android.widget.ImageButton', 'android.widget.Button']
                if re.search(r'https://\w+\.\w+', src_event['text']):  # e.g., a15-a1x-b12
                    tgt_classes.append('android.widget.EditText')
            elif src_event['action'][0].startswith('wait_until_text_presence'):  # e.g., a53-a51-b51, a53-a52-b51
                tgt_classes.append('android.widget.EditText')
        elif src_class == 'android.widget.EditText':
            tgt_classes.append('android.widget.MultiAutoCompleteTextView')  # a43-a41-b42
            if src_event['action'][0].startswith('wait_until_text_presence'):  # e.g., a51-a53-b51
                tgt_classes.append('android.widget.TextView')
            elif re.search(r'https://\w+\.\w+', src_event['text']):  # e.g., a11-a15-b12
                tgt_classes.append('android.widget.TextView')
        elif src_class == 'android.widget.MultiAutoCompleteTextView':  # a41-a43-b42
            tgt_classes.append('android.widget.EditText')

        for w in widgets:
            need_evaluate = False
            if w['class'] in tgt_classes:
                if 'password' in w and w['password'] != is_password:
                    continue
                if 'clickable' in w:  # a dynamic widget
                    if w['clickable'] == is_clickable:
                        need_evaluate = True
                    elif 'action' in src_event and 'class' in w:
                        # if checking oracle, no need to check clickable consistence for TextView and EditText
                        # (e.g., a53-a51-b51 and a51-a53-b51)
                        if src_event['action'][0].startswith('wait_until') \
                                and w['class'] in ['android.widget.EditText', 'android.widget.TextView']:
                            need_evaluate = True
                        elif src_event['action'][0].startswith('swipe') and w['class'] in ['android.widget.TextView']:
                            # a21-a25-b22; no need to check clickable consistence for TextView and swipe action
                            need_evaluate = True
                else:  # a static widget
                    need_evaluate = True
            score = WidgetUtil.weighted_sim(w, src_event, use_stopwords, cross_check) if need_evaluate else None
            # if w['class'] in tgt_classes:
            #     print(w, score)
            if score:
                similars.append((w, score))
        similars.sort(key=lambda x: x[1], reverse=True)
        return similars

    @classmethod
    def get_nearest_button(cls, dom, w):
        # for now just return the first btn on the screen; todo: find the nearest button
        soup = BeautifulSoup(dom, 'lxml')
        for btn_class in ['android.widget.ImageButton', 'android.widget.Button', 'android.widget.EditText']:
            all_btns = soup.find_all('', btn_class)
            if all_btns and len(all_btns) > 0:
                return cls.get_widget_from_soup_element(all_btns[0])
        return None
