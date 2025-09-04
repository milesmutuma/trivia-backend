-- Seed sample categories for testing
INSERT INTO categories (name, description, icon_url) VALUES
('General Knowledge', 'Test your general knowledge across various topics', 'https://example.com/icons/general.png'),
('Science & Nature', 'Questions about biology, chemistry, physics, and nature', 'https://example.com/icons/science.png'),
('History', 'Historical events, figures, and civilizations', 'https://example.com/icons/history.png'),
('Geography', 'Countries, capitals, landmarks, and physical geography', 'https://example.com/icons/geography.png'),
('Sports & Recreation', 'Sports, games, and recreational activities', 'https://example.com/icons/sports.png'),
('Entertainment', 'Movies, music, TV shows, and celebrities', 'https://example.com/icons/entertainment.png'),
('Technology', 'Computers, internet, gadgets, and digital innovation', 'https://example.com/icons/technology.png'),
('Art & Literature', 'Paintings, sculptures, books, and famous authors', 'https://example.com/icons/art.png');

-- Seed sample questions for testing
INSERT INTO questions (category_id, question_text, correct_answer, incorrect_answers, explanation, difficulty, time_limit_seconds) VALUES
-- General Knowledge questions
(1, 'What is the capital city of Australia?', 'Canberra', ARRAY['Sydney', 'Melbourne', 'Brisbane'], 'Canberra is the capital city of Australia, often confused with Sydney which is the largest city.', 'EASY', 30),
(1, 'Which planet is known as the Red Planet?', 'Mars', ARRAY['Venus', 'Jupiter', 'Saturn'], 'Mars is called the Red Planet due to iron oxide (rust) on its surface giving it a reddish appearance.', 'EASY', 20),
(1, 'How many sides does a hexagon have?', '6', ARRAY['5', '7', '8'], 'A hexagon is a polygon with six sides and six angles.', 'EASY', 15),

-- Science & Nature questions
(2, 'What is the chemical symbol for gold?', 'Au', ARRAY['Go', 'Gd', 'Ag'], 'Gold''s chemical symbol Au comes from the Latin word "aurum" meaning gold.', 'MEDIUM', 25),
(2, 'Which gas makes up approximately 78% of Earth''s atmosphere?', 'Nitrogen', ARRAY['Oxygen', 'Carbon Dioxide', 'Argon'], 'Nitrogen makes up about 78% of Earth''s atmosphere, with oxygen being the second most abundant at 21%.', 'MEDIUM', 30),
(2, 'What is the powerhouse of the cell?', 'Mitochondria', ARRAY['Nucleus', 'Ribosome', 'Endoplasmic Reticulum'], 'Mitochondria are called the powerhouse of the cell because they produce ATP, the cell''s main energy source.', 'EASY', 25),

-- History questions
(3, 'In which year did World War II end?', '1945', ARRAY['1944', '1946', '1943'], 'World War II ended in 1945 with Germany surrendering in May and Japan surrendering in September.', 'MEDIUM', 30),
(3, 'Who was the first person to walk on the moon?', 'Neil Armstrong', ARRAY['Buzz Aldrin', 'Michael Collins', 'John Glenn'], 'Neil Armstrong was the first person to set foot on the moon on July 20, 1969, during the Apollo 11 mission.', 'EASY', 25),
(3, 'Which ancient wonder of the world was located in Alexandria?', 'Lighthouse of Alexandria', ARRAY['Hanging Gardens', 'Colossus of Rhodes', 'Statue of Zeus'], 'The Lighthouse of Alexandria (Pharos of Alexandria) was one of the Seven Wonders of the Ancient World.', 'HARD', 45),

-- Geography questions
(4, 'What is the longest river in the world?', 'Nile River', ARRAY['Amazon River', 'Yangtze River', 'Mississippi River'], 'The Nile River in Africa is considered the longest river in the world at approximately 6,650 kilometers.', 'MEDIUM', 30),
(4, 'Which country has the most natural lakes?', 'Canada', ARRAY['Finland', 'Sweden', 'Russia'], 'Canada has more natural lakes than the rest of the world combined, with over 2 million lakes.', 'HARD', 40),
(4, 'What is the smallest country in the world?', 'Vatican City', ARRAY['Monaco', 'Nauru', 'San Marino'], 'Vatican City is the smallest sovereign state in the world with an area of just 0.17 square miles.', 'MEDIUM', 25),

-- Technology questions
(7, 'What does "HTTP" stand for?', 'HyperText Transfer Protocol', ARRAY['HyperText Transmission Protocol', 'HyperLink Transfer Protocol', 'HyperMedia Transfer Protocol'], 'HTTP stands for HyperText Transfer Protocol, the foundation of data communication on the web.', 'MEDIUM', 35),
(7, 'Who founded Microsoft?', 'Bill Gates and Paul Allen', ARRAY['Steve Jobs and Steve Wozniak', 'Larry Page and Sergey Brin', 'Mark Zuckerberg'], 'Microsoft was founded by Bill Gates and Paul Allen in 1975.', 'EASY', 25),
(7, 'What does "AI" stand for in technology?', 'Artificial Intelligence', ARRAY['Automated Intelligence', 'Advanced Intelligence', 'Algorithmic Intelligence'], 'AI stands for Artificial Intelligence, the simulation of human intelligence in machines.', 'EASY', 20);