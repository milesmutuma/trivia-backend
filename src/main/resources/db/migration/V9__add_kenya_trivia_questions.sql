-- Migration to add Kenya-specific trivia questions
-- Version: V9
-- Description: Adds comprehensive Kenya trivia questions across multiple categories

-- First, add Kenya-specific categories if they don't exist
INSERT INTO categories (name, description, icon_url) VALUES
('Kenya - History & Independence', 'Questions about Kenya''s colonial history, independence struggle, and key historical figures', 'https://example.com/icons/kenya-history.png'),
('Kenya - Geography', 'Questions about Kenya''s physical geography, counties, landmarks, and natural features', 'https://example.com/icons/kenya-geography.png'),
('Kenya - Culture & Food', 'Questions about Kenyan traditions, languages, cuisine, and cultural practices', 'https://example.com/icons/kenya-culture.png'),
('Kenya - Sports & Athletics', 'Questions about Kenya''s sporting achievements, athletes, and athletic dominance', 'https://example.com/icons/kenya-sports.png'),
('Kenya - Wildlife & Conservation', 'Questions about Kenya''s wildlife, national parks, and conservation efforts', 'https://example.com/icons/kenya-wildlife.png'),
('Kenya - Modern Kenya', 'Questions about contemporary Kenya, its development, and current affairs', 'https://example.com/icons/kenya-modern.png')
ON CONFLICT (name) DO NOTHING;

-- Add Kenya History & Independence questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'When did Kenya gain independence from British colonial rule?',
    'December 12, 1963',
    ARRAY['January 1, 1960', 'June 1, 1965', 'October 20, 1964'],
    'Kenya gained independence from Britain on December 12, 1963, marking the end of colonial rule.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - History & Independence';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Who was Kenya''s first President?',
    'Jomo Kenyatta',
    ARRAY['Daniel arap Moi', 'Mwai Kibaki', 'Uhuru Kenyatta'],
    'Jomo Kenyatta became Kenya''s first President in 1964 after serving as Prime Minister at independence.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - History & Independence';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does the Swahili backronym for Mau Mau mean?',
    'Let the foreigner go back abroad, let the African regain independence',
    ARRAY['Freedom fighters of Kenya', 'Land and freedom army', 'United for independence'],
    'The Mau Mau adopted the Swahili backronym: "Mzungu Aende Ulaya, Mwafrika Apate Uhuru".',
    'HARD',
    40
FROM categories c WHERE c.name = 'Kenya - History & Independence';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Who was the Field Marshal of the Mau Mau whose capture in 1956 signaled the defeat of the uprising?',
    'Dedan Kimathi',
    ARRAY['Jomo Kenyatta', 'Tom Mboya', 'Oginga Odinga'],
    'Field Marshal Dedan Kimathi was captured on October 21, 1956, and was subsequently executed for his role in the uprising.',
    'MEDIUM',
    35
FROM categories c WHERE c.name = 'Kenya - History & Independence';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Between which years did the Mau Mau uprising take place?',
    '1952-1960',
    ARRAY['1948-1955', '1960-1963', '1945-1950'],
    'The Mau Mau uprising occurred from 1952 to 1960, also known as the Kenya Emergency.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - History & Independence';

-- Add Kenya Geography questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the second-highest mountain in Africa located in Kenya?',
    'Mount Kenya',
    ARRAY['Mount Kilimanjaro', 'Mount Elgon', 'Aberdare Ranges'],
    'Mount Kenya is Africa''s second-highest peak at 5,199 meters, after Mount Kilimanjaro.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Geography';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which is the largest desert lake in the world found in Kenya?',
    'Lake Turkana',
    ARRAY['Lake Victoria', 'Lake Naivasha', 'Lake Nakuru'],
    'Lake Turkana, also known as the Jade Sea, is the world''s largest permanent desert lake.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Geography';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which Kenyan lake is famous for its flamingos?',
    'Lake Nakuru',
    ARRAY['Lake Naivasha', 'Lake Baringo', 'Lake Magadi'],
    'Lake Nakuru is world-famous for its spectacular flamingo populations that feed on the lake''s algae.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Geography';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does ''Nairobi'' mean in the Maasai language?',
    'Place of cool waters',
    ARRAY['Green city', 'Capital city', 'Meeting place'],
    'Nairobi comes from the Maasai phrase "Enkare Nairobi" meaning "place of cool waters".',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Geography';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which is the longest river in Kenya?',
    'Tana River',
    ARRAY['Athi River', 'Nzoia River', 'Yala River'],
    'The Tana River is Kenya''s longest river, flowing approximately 1,014 kilometers to the Indian Ocean.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Geography';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'How many lakes are in the Kenyan Rift Valley?',
    '8',
    ARRAY['6', '10', '12'],
    'Eight lakes make up the main lakes in the Kenyan Rift Valley, including Lakes Turkana, Baringo, Bogoria, Nakuru, Elmenteita, Naivasha, and Magadi.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Geography';

-- Add Kenya Culture & Food questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is Kenya''s unofficial national dish?',
    'Nyama Choma',
    ARRAY['Ugali', 'Pilau', 'Chapati'],
    'Nyama Choma (roasted meat) is considered Kenya''s unofficial national dish, typically enjoyed at social gatherings.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does ''Ugali'' primarily consist of?',
    'Maize flour',
    ARRAY['Wheat flour', 'Rice', 'Cassava'],
    'Ugali is a staple food made from maize (corn) flour cooked with water to a thick porridge-like consistency.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does ''Sukuma Wiki'' literally mean in Swahili?',
    'Push the week',
    ARRAY['Green vegetables', 'Daily meal', 'Cheap food'],
    'Sukuma Wiki literally means "push the week" - it''s an affordable vegetable dish that helps families stretch their budget.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What are the two official languages of Kenya?',
    'Swahili and English',
    ARRAY['English and Kikuyu', 'Swahili and Luo', 'English and Kalenjin'],
    'Kenya''s two official languages are Swahili (Kiswahili) and English, as established in the constitution.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the traditional fermented milk drink of the Kalenjin people?',
    'Mursik',
    ARRAY['Busaa', 'Muratina', 'Chang''aa'],
    'Mursik is a traditional fermented milk stored in specially prepared gourds, cherished by the Kalenjin community.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does ''Harambee'' mean in Swahili?',
    'Let''s pull together',
    ARRAY['Freedom', 'Unity', 'Progress'],
    'Harambee means "let''s pull together" and is Kenya''s national motto, promoting community cooperation.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the name of the traditional Maasai shawl?',
    'Shuka',
    ARRAY['Kanga', 'Kitenge', 'Lesso'],
    'The Shuka is a traditional Maasai shawl, typically featuring bright red colors with distinctive patterns.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Culture & Food';

-- Add Kenya Sports & Athletics questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Who was Kenya''s first Olympic gold medalist in 1968?',
    'Naftali Temu',
    ARRAY['Kipchoge Keino', 'Henry Rono', 'Wilson Kiprugut'],
    'Naftali Temu won Kenya''s first Olympic gold medal in the 10,000 meters at the 1968 Mexico City Olympics.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which Kenyan broke the marathon world record in 2011 with a time of 2:03:38?',
    'Patrick Makau',
    ARRAY['Eliud Kipchoge', 'Wilson Kipsang', 'Dennis Kimetto'],
    'Patrick Makau Musyoki set the marathon world record of 2:03:38 at the 2011 Berlin Marathon.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the nickname of Kenya''s national football team?',
    'Harambee Stars',
    ARRAY['Safari Stars', 'Kenya Warriors', 'The Lions'],
    'Kenya''s national football team is nicknamed the Harambee Stars, reflecting the national motto.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the nickname of Kenya''s national rugby sevens team?',
    'Shujaa',
    ARRAY['Simba', 'Warriors', 'Safari Sevens'],
    'Kenya''s national rugby sevens team is known as Shujaa, which means "brave" or "hero" in Swahili.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which annual rugby tournament is held in Nairobi?',
    'Safari Sevens',
    ARRAY['Kenya Cup', 'Tusker Cup', 'Nairobi Sevens'],
    'The Safari Sevens is an annual international rugby sevens tournament held in Nairobi.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which Kenyan ethnic group is world-famous for producing Olympic champion runners?',
    'Kalenjin',
    ARRAY['Kikuyu', 'Luo', 'Maasai'],
    'The Kalenjin ethnic group has produced a disproportionate number of world-class distance runners.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Sports & Athletics';

-- Add Kenya Wildlife & Conservation questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What are the ''Big Five'' animals in Kenya?',
    'Lion, Leopard, Elephant, Buffalo, Rhino',
    ARRAY['Lion, Cheetah, Elephant, Giraffe, Rhino', 'Lion, Leopard, Cheetah, Elephant, Buffalo', 'Elephant, Giraffe, Zebra, Lion, Rhino'],
    'The Big Five are lion, leopard, elephant, buffalo, and rhinoceros - originally named by big-game hunters.',
    'EASY',
    30
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What does ''Mara'' mean in the Maasai language?',
    'Spotted',
    ARRAY['Great', 'Wide', 'Plains'],
    'Mara means "spotted" in Maa language, referring to the trees dotting the landscape.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Approximately how many wildebeest participate in the Great Migration?',
    '1.2 million',
    ARRAY['500,000', '2 million', '800,000'],
    'About 1.2 million wildebeest participate in the annual Great Migration between Kenya and Tanzania.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which is the national bird of Kenya?',
    'Lilac-breasted roller',
    ARRAY['Secretary bird', 'African fish eagle', 'Crowned crane'],
    'The Lilac-breasted roller, with its stunning colorful plumage, is Kenya''s national bird.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'How many bird species have been identified in the Maasai Mara?',
    'Over 500',
    ARRAY['About 300', 'Around 400', 'Nearly 700'],
    'Over 500 bird species have been recorded in the Maasai Mara, including 60 species of raptors.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the area of the Maasai Mara National Reserve in square kilometers?',
    '1,510',
    ARRAY['2,000', '1,000', '3,000'],
    'The Maasai Mara National Reserve covers approximately 1,510 square kilometers.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which animal can consume up to 300 pounds of food per day?',
    'African Elephant',
    ARRAY['Hippopotamus', 'Giraffe', 'Buffalo'],
    'African elephants can consume up to 300 pounds (136 kg) of vegetation daily.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Wildlife & Conservation';

-- Add Modern Kenya questions
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What is the capital city of Kenya?',
    'Nairobi',
    ARRAY['Mombasa', 'Kisumu', 'Nakuru'],
    'Nairobi is Kenya''s capital and largest city, serving as the country''s economic and political center.',
    'EASY',
    20
FROM categories c WHERE c.name = 'Kenya - Modern Kenya';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Which body of water borders Kenya to the east?',
    'Indian Ocean',
    ARRAY['Atlantic Ocean', 'Red Sea', 'Mediterranean Sea'],
    'The Indian Ocean forms Kenya''s eastern border, with a coastline of approximately 536 kilometers.',
    'EASY',
    25
FROM categories c WHERE c.name = 'Kenya - Modern Kenya';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'Approximately how many ethnic groups are there in Kenya?',
    'Over 40',
    ARRAY['About 20', 'Around 30', 'Nearly 60'],
    'Kenya is home to over 40 different ethnic groups, making it culturally diverse.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Modern Kenya';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'When did Kenya become a republic?',
    '1964',
    ARRAY['1963', '1965', '1962'],
    'Kenya became a republic on December 12, 1964, exactly one year after gaining independence.',
    'MEDIUM',
    30
FROM categories c WHERE c.name = 'Kenya - Modern Kenya';

INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds)
SELECT
    c.id,
    'What year did the British government pay compensation to Mau Mau torture victims?',
    '2013',
    ARRAY['2010', '2015', '2008'],
    'In 2013, the UK government settled with 5,228 Mau Mau victims, paying £19.9 million in compensation.',
    'HARD',
    35
FROM categories c WHERE c.name = 'Kenya - Modern Kenya';

-- Migration completed successfully
-- Added comprehensive Kenya trivia questions across 6 categories