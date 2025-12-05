// EXPECT OK
// Object hierarchy with inheritance-like structure and calculations

struct Animal {
    int age;
    int weight;
};

struct Dog {
    struct Animal base;
    int loyalty;
};

struct Cat {
    struct Animal base;
    int independence;
};

int calculate_score(struct Animal *a) {
    return (*a).age * (*a).weight;
}

int main(void) {
    struct Dog dog;
    struct Cat cat;
    int dog_score;
    int cat_score;
    int result;
    dog.base.age = 3;
    dog.base.weight = 10;
    dog.loyalty = 10;
    cat.base.age = 2;
    cat.base.weight = 5;
    cat.independence = 8;
    dog_score = calculate_score(&dog.base);
    cat_score = calculate_score(&cat.base);
    result = dog_score + cat_score;
    return result;
}
