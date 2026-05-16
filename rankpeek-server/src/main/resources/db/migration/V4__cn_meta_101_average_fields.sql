alter table cn_champion_stats add column avg_damage decimal(12,2);
alter table cn_champion_stats add column avg_damage_taken decimal(12,2);
alter table cn_champion_stats add column avg_heal decimal(12,2);
alter table cn_champion_stats add column avg_duration_seconds integer;
alter table cn_champion_stats add column avg_kills decimal(7,2);
alter table cn_champion_stats add column avg_assists decimal(7,2);
alter table cn_champion_stats add column data_source_note varchar(255);
