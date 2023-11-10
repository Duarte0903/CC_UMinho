import CostCentre (CostCentre(cc_loc))
any :: (a -> Bool) -> [a] -> Bool 
any _ [] = False 
any f (x:y)
    | f x = True 
    | otherwise = Main.any f y 

zipWith :: (a -> b -> c) -> [a] -> [b] -> [c]
zipWith _ [] [] = []
zipWith f (x:xs) (y:ys) = f x y : Main.zipWith f xs ys 

takeWhile :: (a -> Bool) -> [a] -> [a]
takeWhile _ [] = []
takeWhile f (x:y)
    | f x = x : Main.takeWhile f y 
    | otherwise = []

dropWhile :: (a -> Bool) -> [a] -> [a]
dropWhile _ [] = []
dropWhile f (x:y)
    | f x = Main.dropWhile f y 
    | otherwise = x:y

span :: (a -> Bool) -> [a] -> ([a],[a])
span _ [] = ([],[])
span f (x:y)
    | f x = (x:as,bs)
    | otherwise = ([],x:y) 
    where (as,bs)= Main.span f y

deleteBy :: (a -> a -> Bool) -> a -> [a] -> [a]
deleteBy _ _ [] = []
deleteBy f p (x:y)
    | f p x = y
    | otherwise = Main.deleteBy f p y

sortOn :: Ord b => (a -> b) -> [a] -> [a]
sortOn  _ [] = []
sortOn f (h:t) = insert h (sortOn f t)
    where insert x [] = [x]
          insert x (y:ys) = if f x > f y then y : insert x ys else x:y:ys



