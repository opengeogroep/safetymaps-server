create table safetymaps.kro (
	lbl varchar not null,
	ord varchar null,
	src varchar not null,
	disabled bool null,
	constraint kro_pkey primary key (lbl)
);

insert into safetymaps.role(role, protected, description) values
('kro', true, 'Benodigd voor het laden van kro');
