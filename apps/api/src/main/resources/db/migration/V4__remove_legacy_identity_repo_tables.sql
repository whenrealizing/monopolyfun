-- 中文注释：轻量本地身份与仓库交付链路已收敛，物理 schema 同步删除旧身份授权表和外部事件去重表。
DROP TABLE IF EXISTS public.oauth_states;
DROP TABLE IF EXISTS public.oauth_identities;
DROP TABLE IF EXISTS public.external_event_dedup;
