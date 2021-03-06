
/******************************************************************************** 
	component-centric-snapshot-description-fsn-uppercase

	Assertion:
	The first letter of the active FSN associated with active concept should be 
	capitalized.

	note: 	due to a large number of exceptions, this implementation is focused on 
			terms edited in the current propspective release

********************************************************************************/

	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('DESC: id=',a.id, ':First letter of the active FSN of active concept not capitalized.') 	
	from curr_description_d a
		join curr_concept_s b
			on a.conceptid = b.id
			and b.active = a.active
	where a.active = 1
	and a.casesignificanceid != '900000000000017005'
	and binary left(a.term,1) not REGEXP '[A-Z]';
	commit;
