-- public 스키마의 테이블마다 행 수를 "rows.테이블=행수" 한 줄로 냅니다. 백업과 복구 스크립트가 함께 씁니다.
-- 백업할 때는 이 줄을 manifest.txt에 적고, 복구한 뒤에는 다시 세어 manifest.txt의 줄과 같은지 봅니다.
-- query_to_xml: 테이블 이름을 넣어 만든 count 문장을 실행하고 결과를 XML로 받습니다. xpath로 그 안의 숫자만 꺼냅니다.
-- 테이블마다 psql을 따로 부르지 않고 한 번에 세려고 쓰는 방법입니다.
select 'rows.' || table_name || '='
       || (xpath('/row/c/text()',
                 query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text
from information_schema.tables
where table_schema = 'public'
  and table_type = 'BASE TABLE'
order by table_name;
