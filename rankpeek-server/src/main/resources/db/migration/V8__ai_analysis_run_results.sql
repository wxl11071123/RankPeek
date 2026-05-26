alter table ai_analysis_runs
    add column request_hash varchar(64);

alter table ai_analysis_runs
    add column response_json text;

alter table ai_analysis_runs
    add column error_message varchar(1024);

alter table ai_analysis_runs
    add column charge_ledger_entry_id bigint;

alter table ai_analysis_runs
    add column refund_ledger_entry_id bigint;

alter table ai_analysis_runs
    add constraint fk_ai_analysis_runs_charge_ledger
        foreign key (charge_ledger_entry_id) references credit_ledger_entries (id);

alter table ai_analysis_runs
    add constraint fk_ai_analysis_runs_refund_ledger
        foreign key (refund_ledger_entry_id) references credit_ledger_entries (id);

create index idx_ai_analysis_runs_user_endpoint_created
    on ai_analysis_runs (user_id, endpoint, created_at);
