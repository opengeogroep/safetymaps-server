ALTER TABlE safetymaps.kladblok
ADD COLUMN vehicle VARCHAR;

insert into role(role, protected, description) values
('kladblokchat_editor_gms', true, 'Benodigd voor het kunnen toevoegen van kladblokregels in de viewer en deze te loggen in GMS');