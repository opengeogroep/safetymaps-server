do $$
declare
	selected_role safetymaps.role%rowtype;
begin
	select * 
	into selected_role
	from safetymaps.role 
	where role = 'kro';
	
	if not found then
		insert into safetymaps.role(role, protected, description) values
		('kro', true, 'Benodigd voor het laden van kro');
	else
		raise notice 'Role already defined';
	end if;
end $$

create table safetymaps.kro (
	lbl varchar not null,
	ord varchar null,
	src varchar not null,
	disabled bool null,
	constraint kro_pkey primary key (lbl)
);

insert into safetymaps.kro(lbl, ord, src, disabled)
values('Adres', null, 'dbk', true);

insert into safetymaps.kro(lbl, ord, src, disabled)
values('OMS nummer', null, 'dbk', true);
