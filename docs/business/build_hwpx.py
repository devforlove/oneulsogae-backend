# -*- coding: utf-8 -*-
"""오늘의 소개 사업계획서 .hwpx(OWPML) 생성기 — Python 표준 라이브러리만 사용."""
import os
import zipfile
from xml.sax.saxutils import escape

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "오늘의 소개_사업계획서.hwpx")

# ---------------------------------------------------------------------------
# 본문 콘텐츠 정의 (블록 리스트)
#   ("title", text) / ("h1", text) / ("h2", text) / ("p", text) / ("bul", text)
#   ("table", {"widths":[...], "rows":[[c,c],...], "header":True})
# ---------------------------------------------------------------------------
def T(widths, rows, header=True):
    return ("table", {"widths": widths, "rows": rows, "header": header})

CONTENT = [
    ("title", "사 업 계 획 서"),
    ("subtitle", "오늘의 소개(Oneulsogae) — 직장인 신원검증 기반 소개팅·소셜 매칭 플랫폼"),
    ("p", "정부지원사업 제출용(PSST 양식). 본 문서의 기술·기능 내용은 실제 구현된 코드를 근거로 작성하였으며, 시장규모·재무·팀 등 코드로 확인할 수 없는 항목은 【작성 필요】로 표시하였습니다."),

    ("h1", "□ 창업아이템 개요 (요약)"),
    T([10630, 31890], [
        ["구분", "내용"],
        ["아이템명", "오늘의 소개(Oneulsogae) — 직장인 신원검증 기반 소개팅·소셜 매칭 플랫폼"],
        ["핵심 가치", "회사 이메일 인증으로 검증된 직장인만 참여하는 신뢰 기반 매칭"],
        ["핵심 기능", "① 1:1 자동 소개팅 매칭 ② 코인 기반 경제 ③ 실시간 채팅 ④ 2:2 팀 결성"],
        ["산출물", "Kotlin/Spring Boot 기반 백엔드(헥사고날 아키텍처), REST API + WebSocket"],
        ["목표 고객", "신원이 검증된 만남을 원하는 직장인(20~40대)"],
        ["차별성", "회사 이메일 도메인 인증으로 허위 프로필·신원 불명 문제를 구조적으로 차단"],
        ["사업화 단계", "1:1 소개팅 핵심 기능 구현 완료, 2:2 팀 매칭 결성까지 구현"],
    ]),

    ("h1", "1. 문제인식 (Problem)"),
    ("h2", "1-1. 창업 배경 및 필요성"),
    ("bul", "직장인의 만남 기회 부족: 장시간 근로와 좁은 생활 반경으로 자연스러운 만남 기회가 구조적으로 부족하다."),
    ("bul", "기존 소개팅앱의 신뢰 문제: 익명·허위 프로필, 신원 미검증으로 인한 사기·불쾌 경험이 이용 이탈의 핵심 원인이다."),
    ("bul", "검증의 부재: 직업·소속을 자기신고에 의존하는 구조는 '검증된 직장인'이라는 가치를 보장하지 못한다."),
    ("h2", "1-2. 목적 및 해결하고자 하는 문제"),
    ("p", "오늘의 소개은 회사 이메일 도메인 인증을 온보딩 필수 절차로 두어, 신원이 검증된 직장인만 매칭 풀에 진입하게 한다. 이를 통해 '상대가 실제 직장인인가'라는 신뢰 비용을 플랫폼이 구조적으로 해결한다."),
    ("bul", "회사 이메일 인증번호 발송·검증 후 회사명 자동 매핑(미매핑 시 직접 입력)"),
    ("bul", "인증 완료(ACTIVE) 사용자만 매칭 대상에 포함"),
    ("bul", "온보딩 단계: ONBOARDING → EMAIL_VERIFICATION_PENDING → COMPANY_NOT_RESOLVED → ACTIVE"),

    ("h1", "2. 실현가능성 (Solution)"),
    ("h2", "2-1. 개발·구현 현황 (이미 동작하는 기능)"),
    ("p", "아래는 실제 코드에 구현되어 동작하는 기능으로, 본 사업의 실현가능성을 뒷받침하는 핵심 근거다."),
    ("p", "① 사용자 온보딩·프로필(USER): 회사 이메일 인증·회사명 매핑으로 정식 가입, 프로필 등록/수정(나이·성별·회사이메일은 변경 불가), 지역 5권역 자동 분류."),
    ("p", "② 1:1 소개팅 매칭(MATCH) — 완전 구현: 자동 소개(PROPOSED) → 관심 표시 → 양측 수락 시 성사(MATCHED). 유효기간 24시간, 만료 자동 처리, 재소개 방지(조합 유니크 제약)."),
    ("p", "③ 코인 경제(COIN) — 완전 구현: 적립(출석/구매/환불)·차감(신청/수락) 원장 + 잔액 물질화 모델, 매칭 실패 시 50% 환불, 코인 상점·잔액 조회."),
    ("p", "④ 실시간 채팅(CHAT) — 완전 구현: WebSocket(STOMP) 실시간 메시지, 메시지 1건당 4쿼리 최적화(N+1 제거), 키셋 페이지네이션, 읽음 처리, 나가기."),
    ("p", "⑤ 2:2 팀 결성(MATCH-Team): 초대 → 수락 → 결성(FORMED), 초대 철회·해체, 같은 성별만 구성, 초대 가능 사용자 검색. (페어링·성사는 향후 구현)"),
    ("p", "⑥ 알람·팝업·인증: 알람 목록(발신자 프로필 포함), 일일 보상 팝업(출석 코인 자동 적립), 환불 안내 팝업, JWT 토큰 회전·로그아웃·재사용 감지."),
    ("p", "⑦ 배치/스케줄(SCHEDULER): 일일 매칭 배치(성별·지역별 풀 관리, 재소개 방지), 매칭 만료 배치(환불 계산·팝업 생성), 사용자 활성 스냅샷 동기화."),

    ("h2", "2-2. 향후 구체화 방안 (개발 로드맵)"),
    T([10630, 13280, 18610], [
        ["항목", "현황", "계획"],
        ["2:2 팀 페어링·성사", "팀 결성·엔티티만 구현", "팀×팀 매칭 알고리즘, 관심/수락, 성사 후 채팅방 생성"],
        ["미팅(N:N) 매칭", "코인 타입만 정의", "N:N 매칭 비즈니스 로직 구현"],
        ["신청식 매칭", "타입(REQUIRED) 정의", "사용자 능동 신청 인터페이스/API"],
        ["차단·선호도", "미구현", "차단/이상형 필터 기반 추천 고도화"],
        ["결제 연동", "코인 적립 구조 존재", "PG 결제 연동(인앱 결제)"],
    ]),
    ("h2", "2-3. 기술적 차별성 및 경쟁력"),
    ("bul", "헥사고날 아키텍처(Ports & Adapters) + CQRS: 도메인 핵심을 인프라에서 분리, 기능 확장·테스트 용이."),
    ("bul", "신원검증 구조: 회사 이메일 인증을 코어 온보딩에 내재화하여 모방하기 어려운 진입장벽 형성."),
    ("bul", "성능 설계: 채팅 메시지 4쿼리 제한, 키셋 페이지네이션, 인덱스 효율을 고려한 쿼리 설계."),
    ("bul", "테스트 체계: 도메인 Kotest 유닛 + API E2E(Testcontainers)로 품질 검증."),

    ("h1", "3. 성장전략 (Scale-up)"),
    ("h2", "3-1. 비즈니스 모델 (코인 경제)"),
    ("p", "오늘의 소개의 핵심 수익원은 코인이다. 매칭 행위(신청/수락)에 코인을 소모하고, 사용자는 코인을 구매하여 충전한다."),
    T([18610, 10630, 13280], [
        ["행위", "코인 비용", "비고"],
        ["소개팅 신청", "32 코인", "DATING_INIT"],
        ["소개팅 수락", "32 코인", "DATING_ACCEPT"],
        ["미팅 신청", "40 코인", "MEETING_INIT(구현 예정)"],
        ["미팅 수락", "40 코인", "MEETING_ACCEPT(구현 예정)"],
        ["매칭 실패 환불", "+16 코인", "수락자에게 신청 비용 50% 환불"],
        ["출석 보상", "무료 적립", "일일 1회"],
    ]),
    ("bul", "수익 구조: 코인 구매(PURCHASE) → 매칭 소모. 무료 적립(출석)으로 리텐션, 유료 충전으로 수익화."),
    ("bul", "단가/패키지 가격 정책: 【작성 필요 — 코인 패키지별 판매 단가/원가/마진】"),
    ("h2", "3-2. 시장 규모 및 진입 전략"),
    ("bul", "목표 시장: 신원검증을 중시하는 직장인 데이팅 시장"),
    ("bul", "시장 규모(TAM/SAM/SOM): 【작성 필요 — 국내 데이팅앱 시장규모, 직장인 타깃 규모 통계】"),
    ("bul", "진입 전략: 【작성 필요 — 특정 회사/산업군 거점 확산, 초기 사용자 확보(CAC) 전략】"),
    ("bul", "성과 목표: 【작성 필요 — 가입자/MAU/유료전환율/매출 목표(연차별)】"),
    ("h2", "3-3. 자금 소요 및 조달 계획"),
    T([21260, 10630, 10630], [
        ["항목", "금액", "비고"],
        ["인건비(개발)", "【작성 필요】", ""],
        ["인프라/서버", "【작성 필요】", "MySQL, 서버, WebSocket 운영"],
        ["마케팅", "【작성 필요】", "초기 사용자 확보"],
        ["기타 운영비", "【작성 필요】", ""],
        ["합계", "【작성 필요】", ""],
    ]),

    ("h1", "4. 팀 구성 (Team)"),
    ("h2", "4-1. 대표자 및 팀원 역량"),
    T([8000, 8000, 9000, 17520], [
        ["구분", "성명", "역할", "주요 경력/역량"],
        ["대표", "【작성 필요】", "", ""],
        ["팀원", "【작성 필요】", "", ""],
    ]),
    ("p", "기술 역량 근거: 헥사고날 아키텍처 기반 멀티모듈 백엔드, CQRS, 실시간 채팅, 배치 시스템을 자체 구현."),
    ("h2", "4-2. 추진 일정 (마일스톤)"),
    T([8000, 10000, 24520], [
        ["단계", "기간", "내용"],
        ["1단계", "완료", "1:1 소개팅 매칭·코인·채팅·온보딩·팀 결성 구현"],
        ["2단계", "【작성 필요】", "2:2 팀 페어링·성사, 결제(PG) 연동"],
        ["3단계", "【작성 필요】", "미팅(N:N), 추천 고도화, 정식 출시"],
        ["4단계", "【작성 필요】", "사용자 확보·수익화·스케일업"],
    ]),
    ("p", "※ 본 사업계획서는 meeple-backend 코드베이스의 실제 구현 기능을 근거로 작성되었습니다."),
]

# ---------------------------------------------------------------------------
# charPr / paraPr id 정의
#   charPr: 0=본문11, 1=제목22, 2=부제14, 3=H1 16bold, 4=H2 13bold, 5=표헤더11bold, 6=표본문10
#   paraPr: 0=본문, 1=제목center, 2=부제center, 3=H1, 4=H2, 5=불릿, 6=표셀
# ---------------------------------------------------------------------------
NS_P = 'xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph" xmlns:hc="http://www.hancom.co.kr/hwpml/2011/core"'

def esc(s):
    return escape(str(s))

_id = [0]
def nextid():
    _id[0] += 1
    return _id[0]

def run_text(text, charpr):
    # 줄바꿈을 lineBreak로 처리할 필요는 없음(블록단위). 단순 텍스트 run.
    return ('<hp:run charPrIDRef="%d"><hp:t>%s</hp:t></hp:run>' % (charpr, esc(text)))

def para(text, parapr, charpr):
    return ('<hp:p id="%d" paraPrIDRef="%d" styleIDRef="0" pageBreak="0" columnBreak="0" merged="0">'
            '%s</hp:p>' % (nextid(), parapr, run_text(text, charpr)))

def empty_para():
    return ('<hp:p id="%d" paraPrIDRef="0" styleIDRef="0" pageBreak="0" columnBreak="0" merged="0">'
            '<hp:run charPrIDRef="0"></hp:run></hp:p>' % nextid())

def cell(text, row, col, width, is_header):
    charpr = 5 if is_header else 6
    bf = 3 if is_header else 2  # 3=헤더(음영), 2=일반 셀
    inner = ('<hp:p id="%d" paraPrIDRef="6" styleIDRef="0" pageBreak="0" columnBreak="0" merged="0">'
             '%s</hp:p>' % (nextid(), run_text(text, charpr)))
    return (
        '<hp:tc name="" header="%d" hasMargin="0" protect="0" editable="0" dirty="0" borderFillIDRef="%d">'
        '<hp:subList id="" textDirection="HORIZONTAL" lineWrap="BREAK" vertAlign="CENTER" '
        'linkListIDRef="0" linkListNextIDRef="0" textWidth="0" textHeight="0" hasTextRef="0" hasNumRef="0">'
        '%s'
        '</hp:subList>'
        '<hp:cellAddr colAddr="%d" rowAddr="%d"/>'
        '<hp:cellSpan colSpan="1" rowSpan="1"/>'
        '<hp:cellSz width="%d" height="2400"/>'
        '<hp:cellMargin left="510" right="510" top="141" bottom="141"/>'
        '</hp:tc>' % (1 if is_header else 0, bf, inner, col, row, width)
    )

def table(spec):
    widths = spec["widths"]
    rows = spec["rows"]
    header = spec.get("header", True)
    total_w = sum(widths)
    ncol = len(widths)
    nrow = len(rows)
    row_h = 2400
    trs = []
    for r, rowdata in enumerate(rows):
        is_h = header and r == 0
        tcs = []
        for c in range(ncol):
            txt = rowdata[c] if c < len(rowdata) else ""
            tcs.append(cell(txt, r, c, widths[c], is_h))
        trs.append('<hp:tr>%s</hp:tr>' % "".join(tcs))
    tbl = (
        '<hp:tbl id="%d" zOrder="0" numberingType="TABLE" textWrap="TOP_AND_BOTTOM" '
        'textFlow="BOTH_SIDES" lock="0" dropcapstyle="None" pageBreak="CELL" repeatHeader="1" '
        'rowCnt="%d" colCnt="%d" cellSpacing="0" borderFillIDRef="2" noAdjust="0">'
        '<hp:sz width="%d" widthRelTo="ABSOLUTE" height="%d" heightRelTo="ABSOLUTE" protect="0"/>'
        '<hp:pos treatAsChar="1" affectLSpacing="0" flowWithText="1" allowOverlap="0" '
        'holdAnchorAndSO="0" vertRelTo="PARA" horzRelTo="COLUMN" vertAlign="TOP" horzAlign="LEFT" '
        'vertOffset="0" horzOffset="0"/>'
        '<hp:outMargin left="0" right="0" top="0" bottom="283"/>'
        '<hp:inMargin left="510" right="510" top="141" bottom="141"/>'
        '%s'
        '</hp:tbl>' % (nextid(), nrow, ncol, total_w, row_h * nrow, "".join(trs))
    )
    # 표는 문단의 run 안에 위치
    return ('<hp:p id="%d" paraPrIDRef="0" styleIDRef="0" pageBreak="0" columnBreak="0" merged="0">'
            '<hp:run charPrIDRef="0">%s</hp:run></hp:p>' % (nextid(), tbl))

def render_block(kind, data):
    if kind == "title":
        return para(data, 1, 1)
    if kind == "subtitle":
        return para(data, 2, 2)
    if kind == "h1":
        return para(data, 3, 3)
    if kind == "h2":
        return para(data, 4, 4)
    if kind == "p":
        return para(data, 0, 0)
    if kind == "bul":
        return para("· " + data, 5, 0)
    if kind == "table":
        return table(data)
    raise ValueError(kind)

# ---------------------------------------------------------------------------
# 섹션 본문 (secPr 포함된 첫 문단 + 콘텐츠)
# ---------------------------------------------------------------------------
def build_section():
    secpr = (
        '<hp:secPr id="" textDirection="HORIZONTAL" spaceColumns="1134" tabStop="8000" '
        'tabStopVal="4000" tabStopUnit="HWPUNIT" outlineShapeIDRef="1" memoShapeIDRef="0" '
        'textVerticalWidthHead="0" masterPageCnt="0">'
        '<hp:grid lineGrid="0" charGrid="0" wonggojiFormat="0" strtnum="0"/>'
        '<hp:startNum pageStartsOn="BOTH" page="0" pic="0" tbl="0" equation="0"/>'
        '<hp:visibility hideFirstHeader="0" hideFirstFooter="0" hideFirstMasterPage="0" '
        'border="SHOW_ALL" fill="SHOW_ALL" hideFirstPageNum="0" hideFirstEmptyLine="0" showLineNumber="0"/>'
        '<hp:lineNumberShape restartType="0" countBy="0" distance="0" startNumber="0"/>'
        '<hp:pagePr landscape="WIDELY" width="59528" height="84188" gutterType="LEFT_ONLY">'
        '<hp:margin header="4252" footer="4252" gutter="0" left="8504" right="8504" top="5668" bottom="4252"/>'
        '</hp:pagePr>'
        '<hp:footNotePr>'
        '<hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar=")" supscript="0"/>'
        '<hp:noteLine length="-1" type="SOLID" width="0.12 mm" color="#000000"/>'
        '<hp:noteSpacing betweenNotes="850" belowLine="567" aboveLine="567"/>'
        '<hp:numbering type="CONTINUOUS" newNum="1"/>'
        '<hp:placement place="EACH_COLUMN" beneathText="0"/>'
        '</hp:footNotePr>'
        '<hp:endNotePr>'
        '<hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar=")" supscript="0"/>'
        '<hp:noteLine length="14692344" type="SOLID" width="0.12 mm" color="#000000"/>'
        '<hp:noteSpacing betweenNotes="0" belowLine="567" aboveLine="850"/>'
        '<hp:numbering type="CONTINUOUS" newNum="1"/>'
        '<hp:placement place="END_OF_DOCUMENT" beneathText="0"/>'
        '</hp:endNotePr>'
        '<hp:pageBorderFill type="BOTH" borderFillIDRef="1" textBorder="PAPER" headerInside="0" '
        'footerInside="0" fillArea="PAPER">'
        '<hp:offset left="1417" right="1417" top="1417" bottom="1417"/>'
        '</hp:pageBorderFill>'
        '</hp:secPr>'
    )
    # 첫 문단: secPr를 run 안에 포함하고 제목 텍스트도 같이
    first = ('<hp:p id="%d" paraPrIDRef="1" styleIDRef="0" pageBreak="0" columnBreak="0" merged="0">'
             '<hp:run charPrIDRef="1">%s<hp:t>%s</hp:t></hp:run></hp:p>'
             % (nextid(), secpr, esc(CONTENT[0][1])))
    body = [first]
    for kind, data in CONTENT[1:]:
        body.append(render_block(kind, data))
    body.append(empty_para())
    sec = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
           '<hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section" '
           'xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph" '
           'xmlns:hc="http://www.hancom.co.kr/hwpml/2011/core">'
           + "".join(body) + '</hs:sec>')
    return sec

# ---------------------------------------------------------------------------
# header.xml
# ---------------------------------------------------------------------------
def fontface(lang):
    return ('<hh:fontface lang="%s" fontCnt="1">'
            '<hh:font id="0" face="함초롬바탕" type="TTF" isEmbedded="0">'
            '<hh:typeInfo familyType="FCAT_MYUNGJO" weight="0" proportion="0" contrast="0" '
            'strokeVariation="0" armStyle="0" letterform="0" midline="0" xHeight="0"/>'
            '</hh:font></hh:fontface>' % lang)

def charpr(cid, height, bold=False, color="#000000"):
    b = '<hh:bold/>' if bold else ''
    return ('<hh:charPr id="%d" height="%d" textColor="%s" shadeColor="none" useFontSpace="0" '
            'useKerning="0" symMark="NONE" borderFillIDRef="1">'
            '<hh:fontRef hangul="0" latin="0" hanja="0" japanese="0" other="0" symbol="0" user="0"/>'
            '<hh:ratio hangul="100" latin="100" hanja="100" japanese="100" other="100" symbol="100" user="100"/>'
            '<hh:spacing hangul="0" latin="0" hanja="0" japanese="0" other="0" symbol="0" user="0"/>'
            '<hh:relSz hangul="100" latin="100" hanja="100" japanese="100" other="100" symbol="100" user="100"/>'
            '<hh:offset hangul="0" latin="0" hanja="0" japanese="0" other="0" symbol="0" user="0"/>'
            '%s</hh:charPr>' % (cid, height, color, b))

def parapr(pid, align="JUSTIFY", before=0, after=0, line=160):
    return ('<hh:paraPr id="%d" tabPrIDRef="0" condense="0" fontLineHeight="0" snapToGrid="1" '
            'suppressLineNumbers="0" checked="0">'
            '<hh:align horizontal="%s" vertical="BASELINE"/>'
            '<hh:heading type="NONE" idRef="0" level="0"/>'
            '<hh:breakSetting breakLatinWord="KEEP_WORD" breakNonLatinWord="KEEP_WORD" '
            'widowOrphan="0" keepWithNext="0" keepLines="0" pageBreakBefore="0" lineWrap="BREAK"/>'
            '<hh:autoSpacing eAsianEng="0" eAsianNum="0"/>'
            '<hh:margin>'
            '<hc:intent value="0" unit="HWPUNIT"/>'
            '<hc:left value="0" unit="HWPUNIT"/>'
            '<hc:right value="0" unit="HWPUNIT"/>'
            '<hc:prev value="%d" unit="HWPUNIT"/>'
            '<hc:next value="%d" unit="HWPUNIT"/>'
            '</hh:margin>'
            '<hh:lineSpacing type="PERCENT" value="%d" unit="HWPUNIT"/>'
            '<hh:border borderFillIDRef="1" offsetLeft="0" offsetRight="0" offsetTop="0" '
            'offsetBottom="0" connect="0" ignoreMargin="0"/>'
            '</hh:paraPr>' % (pid, align, before, after, line))

def borderfill(bid, shade=None, bordered=False):
    if bordered:
        edge = ('<hh:%s type="SOLID" width="0.12 mm" color="#000000"/>')
        edges = (edge % "leftBorder") + (edge % "rightBorder") + (edge % "topBorder") + (edge % "bottomBorder")
        diag = '<hh:diagonal type="SOLID" width="0.12 mm" color="#000000"/>'
    else:
        edge = ('<hh:%s type="NONE" width="0.1 mm" color="#000000"/>')
        edges = (edge % "leftBorder") + (edge % "rightBorder") + (edge % "topBorder") + (edge % "bottomBorder")
        diag = '<hh:diagonal type="SOLID" width="0.1 mm" color="#000000"/>'
    if shade:
        fill = ('<hc:fillBrush><hc:winBrush faceColor="%s" hatchColor="#000000" alpha="0"/></hc:fillBrush>' % shade)
    else:
        fill = '<hc:fillBrush><hc:winBrush faceColor="none" hatchColor="#000000" alpha="0"/></hc:fillBrush>'
    return ('<hh:borderFill id="%d" threeD="0" shadow="0" centerLine="NONE" breakCellSeparateLine="0">'
            '<hh:slash type="NONE" Crooked="0" isCounter="0"/>'
            '<hh:backSlash type="NONE" Crooked="0" isCounter="0"/>'
            '%s%s%s</hh:borderFill>' % (bid, edges, diag, fill))

def build_header():
    langs = ["HANGUL", "LATIN", "HANJA", "JAPANESE", "OTHER", "SYMBOL", "USER"]
    fontfaces = "".join('<hh:fontfaces itemCnt="1">%s</hh:fontfaces>' % fontface(l) for l in langs)
    bfs = [
        borderfill(1, shade=None, bordered=False),   # 무테(기본)
        borderfill(2, shade=None, bordered=True),    # 표 일반 셀
        borderfill(3, shade="#E6E6E6", bordered=True),  # 표 헤더(음영)
    ]
    border_fills = '<hh:borderFills itemCnt="%d">%s</hh:borderFills>' % (len(bfs), "".join(bfs))
    charprs = [
        charpr(0, 1100),                 # 본문 11
        charpr(1, 2200, bold=True),      # 제목 22
        charpr(2, 1400, bold=True),      # 부제 14
        charpr(3, 1600, bold=True),      # H1 16
        charpr(4, 1300, bold=True),      # H2 13
        charpr(5, 1100, bold=True),      # 표헤더 11 bold
        charpr(6, 1000),                 # 표본문 10
    ]
    char_props = '<hh:charProperties itemCnt="%d">%s</hh:charProperties>' % (len(charprs), "".join(charprs))
    paraprs = [
        parapr(0, "JUSTIFY", before=0, after=100, line=160),   # 본문
        parapr(1, "CENTER", before=0, after=300, line=160),    # 제목
        parapr(2, "CENTER", before=0, after=600, line=160),    # 부제
        parapr(3, "LEFT", before=600, after=200, line=160),    # H1
        parapr(4, "LEFT", before=400, after=150, line=160),    # H2
        parapr(5, "JUSTIFY", before=0, after=50, line=160),    # 불릿
        parapr(6, "LEFT", before=0, after=0, line=150),        # 표셀
    ]
    para_props = '<hh:paraProperties itemCnt="%d">%s</hh:paraProperties>' % (len(paraprs), "".join(paraprs))
    tab_props = ('<hh:tabProperties itemCnt="1">'
                 '<hh:tabPr id="0" autoTabLeft="0" autoTabRight="0"/>'
                 '</hh:tabProperties>')
    numberings = ('<hh:numberings itemCnt="1">'
                  '<hh:numbering id="1" start="0">'
                  '<hh:paraHead start="1" level="1" align="LEFT" useInstWidth="1" autoIndent="1" '
                  'widthAdjust="0" textOffsetType="PERCENT" textOffset="50" numFormat="DIGIT" '
                  'charPrIDRef="4294967295" checkable="0">^1.</hh:paraHead>'
                  '</hh:numbering></hh:numberings>')
    styles = ('<hh:styles itemCnt="1">'
              '<hh:style id="0" type="PARA" name="바탕글" engName="Normal" paraPrIDRef="0" '
              'charPrIDRef="0" nextStyleIDRef="0" langID="1042" lockForm="0"/>'
              '</hh:styles>')
    reflist = ('<hh:refList>%s%s%s%s%s%s%s</hh:refList>'
               % (fontfaces, border_fills, char_props, tab_props, numberings, para_props, styles))
    head = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            '<hh:head xmlns:hh="http://www.hancom.co.kr/hwpml/2011/head" '
            'xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph" '
            'xmlns:hc="http://www.hancom.co.kr/hwpml/2011/core" version="1.4" secCnt="1">'
            '<hh:beginNum page="1" footnote="1" endnote="1" pic="1" tbl="1" equation="1"/>'
            + reflist + '</hh:head>')
    return head

# ---------------------------------------------------------------------------
# 패키지 메타 파일들
# ---------------------------------------------------------------------------
VERSION_XML = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<hv:HCFVersion xmlns:hv="http://www.hancom.co.kr/hwpml/2011/version" '
    'tagetApplication="WORDPROCESSOR" major="5" minor="1" micro="1" buildNumber="0" '
    'os="10" xmlVersion="1.4" application="Hancom Office Hangul" appVersion="9, 1, 1, 5656"/>')

CONTAINER_XML = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<ocf:container xmlns:ocf="urn:oasis:names:tc:opendocument:xmlns:container" '
    'xmlns:hpf="http://www.hancom.co.kr/schema/2011/hpf">'
    '<ocf:rootfiles>'
    '<ocf:rootfile full-path="Contents/content.hpf" media-type="application/hwpml-package+xml"/>'
    '</ocf:rootfiles></ocf:container>')

MANIFEST_XML = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<odf:manifest xmlns:odf="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" version="1.4">'
    '<odf:file-entry full-path="Contents/header.xml" media-type="application/xml"/>'
    '<odf:file-entry full-path="Contents/section0.xml" media-type="application/xml"/>'
    '</odf:manifest>')

CONTENT_HPF = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<opf:package xmlns:opf="http://www.idpf.org/2007/opf/" '
    'xmlns:dc="http://purl.org/dc/elements/1.1/" '
    'xmlns:ha="http://www.hancom.co.kr/hwpml/2011/app" '
    'version="" unique-identifier="" id="">'
    '<opf:metadata>'
    '<opf:title>오늘의 소개 사업계획서</opf:title>'
    '<opf:language>ko</opf:language>'
    '<opf:meta name="creator" content="oneulsogae"/>'
    '</opf:metadata>'
    '<opf:manifest>'
    '<opf:item id="header" href="Contents/header.xml" media-type="application/xml"/>'
    '<opf:item id="section0" href="Contents/section0.xml" media-type="application/xml"/>'
    '<opf:item id="settings" href="settings.xml" media-type="application/xml"/>'
    '</opf:manifest>'
    '<opf:spine>'
    '<opf:itemref idref="section0" linear="yes"/>'
    '</opf:spine>'
    '</opf:package>')

SETTINGS_XML = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<ha:HWPApplicationSetting xmlns:ha="http://www.hancom.co.kr/hwpml/2011/app" '
    'xmlns:config="http://www.hancom.co.kr/hwpml/2011/configItem">'
    '<ha:CaretPosition listIDRef="0" paraIDRef="0" pos="0"/>'
    '</ha:HWPApplicationSetting>')

def prv_text():
    parts = []
    for kind, data in CONTENT:
        if kind in ("title", "subtitle", "h1", "h2", "p", "bul"):
            parts.append(str(data))
    return "\n".join(parts)

# ---------------------------------------------------------------------------
# zip 패키징
# ---------------------------------------------------------------------------
def main():
    header = build_header()
    section = build_section()
    files = {
        "version.xml": VERSION_XML,
        "META-INF/container.xml": CONTAINER_XML,
        "META-INF/manifest.xml": MANIFEST_XML,
        "Contents/content.hpf": CONTENT_HPF,
        "Contents/header.xml": header,
        "Contents/section0.xml": section,
        "settings.xml": SETTINGS_XML,
        "Preview/PrvText.txt": prv_text(),
    }
    if os.path.exists(OUT):
        os.remove(OUT)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        # mimetype 은 반드시 첫 엔트리 + 무압축(STORED)
        zi = zipfile.ZipInfo("mimetype")
        zi.compress_type = zipfile.ZIP_STORED
        z.writestr(zi, "application/hwp+zip")
        for name, content in files.items():
            z.writestr(name, content.encode("utf-8"))
    print("WROTE", OUT)

if __name__ == "__main__":
    main()
