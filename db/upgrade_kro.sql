create table safetymaps.kro (
	lbl varchar not null,
	ord varchar null,
	src varchar not null,
	disabled bool null,
	constraint kro_pkey primary key (lbl)
);
