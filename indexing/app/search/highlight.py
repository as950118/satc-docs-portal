"""Search excerpts built in the application, in the ES highlight format: HTML-escaped text with matches in <em>.

Lengths count original characters, so <em> tags are excluded and an escaped entity such as &lt; counts as one.
"""
import html
import re
from dataclasses import dataclass, field
from typing import List, Optional, Set, Tuple

MIN_NEEDLE = 2

Span = Tuple[int, int]


def query_terms(query: str) -> List[str]:
    seen, terms = set(), []
    for term in query.split():
        if term.casefold() not in seen:
            seen.add(term.casefold())
            terms.append(term)
    return terms


def term_spans(text: str, term: str) -> List[Span]:
    """Case-insensitive occurrences of the term. A term with a particle such as "스타일을" that is not in the
    text is retried with one fewer trailing character until a match or a two-character prefix remains."""
    needle = term
    while len(needle) >= MIN_NEEDLE:
        spans = [m.span() for m in re.finditer(re.escape(needle), text, re.IGNORECASE)]
        if spans:
            return spans
        needle = needle[:-1]
    return []


def _matches(text: str, query: str) -> List[Tuple[int, int, int]]:
    return sorted((s, e, i) for i, term in enumerate(query_terms(query)) for s, e in term_spans(text, term))


def _mark(text: str, lo: int, hi: int, spans: List[Span]) -> str:
    out, pos = [], lo
    for s, e in _union(spans):
        s, e = max(s, lo), min(e, hi)
        if s >= e:
            continue
        out += [html.escape(text[pos:s]), "<em>", html.escape(text[s:e]), "</em>"]
        pos = e
    out.append(html.escape(text[pos:hi]))
    return "".join(out)


def _union(spans: List[Span]) -> List[Span]:
    merged: List[List[int]] = []
    for s, e in sorted(spans):
        if merged and s <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], e)
        else:
            merged.append([s, e])
    return [(s, e) for s, e in merged]


def highlight_whole(text: str, query: str) -> Optional[str]:
    """The whole text with matches marked, or None when nothing matches. Used for titles."""
    spans = [(s, e) for s, e, _ in _matches(text, query)]
    return _mark(text, 0, len(text), spans) if spans else None


@dataclass
class _Group:
    start: int
    end: int
    terms: Set[int] = field(default_factory=set)
    count: int = 0


def excerpts(text: str, query: str, fragment_size: int, max_fragments: int) -> List[str]:
    """Up to max_fragments windows of at most fragment_size characters, ranked by distinct matched terms
    and then by match count, returned in text order."""
    matches = _matches(text, query)
    groups: List[_Group] = []
    for s, e, term in matches:
        if groups and e - groups[-1].start <= fragment_size:
            g = groups[-1]
            g.end = max(g.end, e)
        else:
            g = _Group(start=s, end=min(e, s + fragment_size))
            groups.append(g)
        g.terms.add(term)
        g.count += 1

    ranked = sorted(range(len(groups)), key=lambda i: (-len(groups[i].terms), -groups[i].count, i))
    chosen = [groups[i] for i in sorted(ranked[:max_fragments])]
    spans = [(s, e) for s, e, _ in matches]

    fragments, prev_hi = [], 0
    for n, g in enumerate(chosen):
        next_lo = chosen[n + 1].start if n + 1 < len(chosen) else len(text)
        lo, hi = _window(text, g, fragment_size, prev_hi, next_lo)
        fragments.append(_mark(text, lo, hi, spans))
        prev_hi = hi
    return fragments


def _window(text: str, g: _Group, size: int, floor: int, ceiling: int) -> Span:
    """Pad the group with context inside [floor, ceiling) so fragments never overlap. Start at a sentence
    start when one is close enough before the match, otherwise center the match and cut at whitespace."""
    slack = size - (g.end - g.start)
    sentence = _sentence_start(text, max(floor, g.start - slack), g.start)
    if sentence is not None:
        lo = sentence
        hi = min(ceiling, lo + size)
    else:
        lo = max(floor, g.start - slack // 2)
        hi = min(ceiling, lo + size)
        lo = max(floor, min(lo, hi - size), 0)
        if lo > 0 and not text[lo - 1].isspace():
            cut = _next_space(text, lo, g.start)
            lo = cut if cut is not None else lo
    if hi < len(text) and not text[hi].isspace():
        cut = _prev_space(text, g.end, hi)
        hi = cut if cut is not None else hi
    while lo < g.start and text[lo].isspace():
        lo += 1
    while hi > g.end and text[hi - 1].isspace():
        hi -= 1
    return lo, hi


SENTENCE_END = re.compile(r"(?:[.?!]\s+|\n+)")


def _sentence_start(text: str, lo: int, limit: int) -> Optional[int]:
    """The earliest sentence start in [lo, limit], so the fragment keeps the most context after it."""
    if lo == 0:
        return 0
    for m in SENTENCE_END.finditer(text, lo - 1, limit):
        if m.end() >= lo:
            return m.end()
    return None


def _next_space(text: str, lo: int, limit: int) -> Optional[int]:
    for i in range(lo, limit):
        if text[i].isspace():
            return i + 1
    return None


def _prev_space(text: str, limit: int, hi: int) -> Optional[int]:
    for i in range(hi - 1, limit - 1, -1):
        if text[i].isspace():
            return i
    return None
