"""포털 검색 발췌 규칙(AC-6): 최대 3개, 태그를 뺀 150자 이내, 일치 부분 <em>, HTML 이스케이프.

실행: cd indexing && python -m pytest tests/test_highlight.py
"""
import html
import re

from app.search.highlight import excerpts, highlight_whole


def plain(fragment):
    return html.unescape(re.sub(r"</?em>", "", fragment))


def marked(fragment):
    return [html.unescape(m) for m in re.findall(r"<em>(.*?)</em>", fragment)]


LONG = " ".join(f"{n}번째 문장은 검색 발췌 규칙을 설명하는 더미 문장이고 길이를 채우려고 덧붙인 말이 조금 더 있다." for n in range(1, 13))


def test_at_most_three_fragments_of_at_most_150_characters_without_tags():
    fragments = excerpts(LONG, "발췌", 150, 3)
    assert len(fragments) == 3
    assert all(len(plain(f)) <= 150 for f in fragments)
    assert all(marked(f) and set(marked(f)) == {"발췌"} for f in fragments)


def test_fragments_are_original_text_in_document_order():
    fragments = excerpts(LONG, "발췌", 150, 3)
    positions = [LONG.index(plain(f)) for f in fragments]
    assert positions == sorted(positions)
    assert positions[0] + len(plain(fragments[0])) <= positions[1]


def test_fragment_with_more_distinct_terms_is_preferred():
    text = ("커밋 " * 3 + "가" * 300 + " 커밋 메시지 형식은 제목을 짧게 쓴다 " + "나" * 300 + " 커밋")
    fragments = excerpts(text, "커밋 메시지", 150, 1)
    assert set(marked(fragments[0])) == {"커밋", "메시지"}


def test_particle_suffix_falls_back_to_the_stem():
    fragments = excerpts("괄호와 들여쓰기 스타일은 팀 규칙을 따른다.", "코딩 스타일을", 150, 3)
    assert [marked(f) for f in fragments] == [["스타일"]]


def test_match_is_case_insensitive_and_keeps_original_case():
    fragments = excerpts("Docker 이미지는 docker 태그를 붙인다", "DOCKER", 150, 3)
    assert marked(fragments[0]) == ["Docker", "docker"]


def test_html_is_escaped_and_entities_count_as_one_character():
    text = "본문에 <b>굵은 글씨</b> & \"따옴표\" 가 있다"
    fragment = excerpts(text, "굵은", 150, 3)[0]
    assert "<b>" not in fragment and "&lt;b&gt;" in fragment and "&amp;" in fragment
    assert plain(fragment) == text
    assert fragment.count("<em>") == 1 and "<em>굵은</em>" in fragment


def test_no_match_in_body_gives_no_fragment():
    assert excerpts("본문에는 다른 말만 있다", "온보딩", 150, 3) == []


def test_empty_body_gives_no_fragment():
    assert excerpts("", "온보딩", 150, 3) == []


def test_single_character_terms_are_not_highlighted():
    assert excerpts("a b c 가 나 다", "가", 150, 3) == []


def test_title_is_marked_whole_or_not_at_all():
    assert highlight_whole("온보딩 체크리스트", "온보딩") == "<em>온보딩</em> 체크리스트"
    assert highlight_whole("온보딩 체크리스트", "배포") is None


def test_chunk_parameters_cap_five_fragments_of_100_characters():
    fragments = excerpts(LONG * 2, "발췌", 100, 5)
    assert len(fragments) == 5 and all(len(plain(f)) <= 100 for f in fragments)
