
/******************************************************************************** 
	component-centric-snapshot-ctv3-ratio

	Assertion:
	There is one and only one CTV3 simple map refset member per concept.

********************************************************************************/
	
	
	/* Concept maps to multiple CTV3 Refset Members */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('CONCEPT: id=',a.referencedcomponentid, ': Concept has more than one associated CTV3 refset member.') 
	from curr_simplemaprefset_s a
	where a.refsetid = '900000000000497000'
	group by a.referencedcomponentid
	having count(a.referencedcomponentid) > 1;
	commit;
	
	
/* create table if not exists of CTV3 refset members */
	drop table if exists v_ctv3;
	create table if not exists v_ctv3 (index (referencedcomponentid)) as
		select referencedcomponentid 
		from curr_simplemaprefset_s 
		where refsetid = '900000000000497000';
		
/* Concept is without a CTV3 Refset Member mapping */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('CONCEPT: id=',a.id, ': Concept does not have an associated CTV3 refset member.') 
	from curr_concept_s a
	left join v_ctv3 b 
		on a.id = b.referencedcomponentid 
	where b.referencedcomponentid is null;

	drop table if exists v_act_ctv3;










	