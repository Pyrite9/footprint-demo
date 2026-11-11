// 선으로 그린 작은 아이콘의 모양입니다. 값은 svg path의 d 속성입니다.
const PATHS = {
    home: 'M3 11l9-8 9 8M5 10v10h14V10',
    file: 'M14 3H6v18h12V7zM14 3v4h4',
    clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
    timeline: 'M3 12h18M7 8v8M12 8v8M17 8v8',
    list: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
    members: 'M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20v-1a5 5 0 0 1 5-5h3a5 5 0 0 1 5 5v1M16 4.6a3.5 3.5 0 0 1 0 6.8M21.5 20v-1a5 5 0 0 0-3.5-4.7',
    person: 'M12 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5 20v-1a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v1',
    evaluation: 'M9 3h6v4H9zM9 5H5v16h14V5h-4M9 14l2 2 4-4',
    plus: 'M12 5v14M5 12h14',
    close: 'M6 6l12 12M18 6L6 18',
    chevron: 'M9 6l6 6-6 6',
    upload: 'M12 16V5M7 10l5-5 5 5M4 20h16',
    download: 'M12 4v11M7 10l5 5 5-5M4 20h16',
    moon: 'M20 14A8 8 0 1 1 10 4a6.5 6.5 0 0 0 10 10z',
    sun: 'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',
    logout: 'M10 4H5v16h5M15 8l4 4-4 4M19 12H9',
    bell: 'M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0',
}

// 아이콘 하나를 그립니다. name으로 고르고, 색은 글자색을 따라갑니다(index.css의 .icon).
// aria-hidden: 뜻은 옆의 글자나 aria-label이 알려 주므로 화면 읽기 프로그램에서는 건너뜁니다.
function Icon({ name }) {
    return (
        <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d={PATHS[name]} />
        </svg>
    )
}

export default Icon
