SET search_path = safetymaps, pg_catalog;

insert into role(role, protected, description) values
('safetyconnect_webservice', true, 'Benodigd voor toegang SC&T SafetyConnect webservice'),
('incidentmonitor', true, 'Benodigd voor toegang incidentmonitor'),
('incidentmonitor_kladblok', true, 'Benodigd voor tonen kladblokregels in incidentmonitor'),
('eigen_voertuignummer', true, 'Benodigd voor zelf kunnen instellen voertuignummer');

update user_roles set role = 'safetyconnect_webservice' where role = 'falck_webservice';
delete from role where role = 'falck_webservice';

insert into settings
select 'safetyconnect_webservice_authorization', value
from safetymaps.settings where name = 'falck_webservice_authorization';
delete from settings where name = 'falck_webservice_authorization';

insert into settings
select 'safetyconnect_webservice_url', value
from safetymaps.settings where name = 'falck_webservice_url';
delete from settings where name = 'falck_webservice_url';

-- TODO: replace organisation.modules incidents options incidentSource value "falckService" with "SafetyConnect"

