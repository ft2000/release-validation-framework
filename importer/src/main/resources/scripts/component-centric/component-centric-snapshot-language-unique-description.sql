
/******************************************************************************** 
	component-centric-snapshot-language-unique-description

	Assertion:
	There is only zero or one active Definition per concept per dialect in 
	the snapshot file.

********************************************************************************/
	
	
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('CONCEPT: id=',a.id, ': Concept contains more than one definition for a given language.') 
	
	from curr_concept_s a
	inner join curr_textdefinition_s b on a.id = b.conceptid
	where a.active = '1' 
	and b.active = '1'
	and b.typeid = '900000000000550004'
	GROUP BY b.conceptid, b.languagecode
	having count(*) > 1;
	

			
