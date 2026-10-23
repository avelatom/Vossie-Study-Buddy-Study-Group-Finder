-- Modules added to database--


USE v2v_db;

INSERT INTO
    MODULES (
        module_code,
        module_name,
        faculty
    )
VALUES (
        'ITMTB1',
        'Mathematics 1B',
        'Information Technology'
    ),
    (
        'ITPPA1',
        'Procedural Programming',
        'Information Technology'
    ),
    (
        'ITPFA1',
        'Non-Technical Skills for IT Professionals',
        'Information Technology'
    ),
    (
        'ITISA1',
        'Introduction to Information Systems',
        'Information Technology'
    ),
    (
        'ITSMA1',
        'Computer Skills (Microsoft)',
        'Information Technology'
    ),
    (
        'ITCNA1',
        'Computer Network and Security',
        'Information Technology'
    ),
    (
        'ITMCA1',
        'Mathematics for Computing',
        'Information Technology'
    ),
    (
        'ITPLA1',
        'Introduction to Programming',
        'Information Technology'
    ),
    (
        'ITAIA1',
        'AI Ethics and Privacy',
        'Information Technology'
    ),
    (
        'ITCTA1',
        'Computer Network Technologies',
        'Information Technology'
    )
ON DUPLICATE KEY UPDATE
    module_name = VALUES(module_name);