package com.flink.platform.web.test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

import static javolution.testing.TestContext.assertEquals;


public class Test {
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "animalType",
            visible = true
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "DOG"),
            @JsonSubTypes.Type(value = Cat.class, name = "CAT")})
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Zoo {
        private String name;
        private AnimalTypeEnum animalType;

        public Zoo(String name, AnimalTypeEnum animalType) {
            this.name = name;
            this.animalType = animalType;
        }
    }



    @Getter
    @Setter
    @NoArgsConstructor
    public static class Dog extends Zoo {
        private Double speed;


        public Dog(String name, AnimalTypeEnum animalType, Double speed) {
            super(name, animalType);
            this.speed = speed;
        }

        public double getSpeed(){
            return this.speed;
        }
    }

    /**
     * 一定要有所有的get set方法和有参无参构造器
     */
    @Data
    @NoArgsConstructor
    public static class Cat extends Zoo {
        private Integer size;

        public Cat(String name, AnimalTypeEnum animalTypeEnum, Integer size) {
            super(name, animalTypeEnum);
            this.size = size;
        }

        public int getSize() {
            return this.size;
        }
    }


    public static void main(String[] args) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Cat cat = new Cat("小猫", AnimalTypeEnum.CAT, 20);
        Dog dog = new Dog("小狗", AnimalTypeEnum.DOG, 30.03);


        String catJson = objectMapper.writeValueAsString(cat);
        String dogJson = objectMapper.writeValueAsString(dog);

        System.out.println(catJson);
        System.out.println(dogJson);

        //反序列化
        Zoo catZoo = objectMapper.readValue(catJson, Zoo.class);
        Zoo dogZoo = objectMapper.readValue(dogJson, Zoo.class);

        System.out.println(catZoo.getClass());
        System.out.println(catZoo.getAnimalType());
        AnimalTypeEnum catE = Enum.valueOf(AnimalTypeEnum.class, "CAT");
        //类型一致
//        assertEquals(catZoo.getClass(),cat.getClass());
//        assertEquals(dogZoo.getClass(),dog.getClass());
//
//        ((Cat)catZoo).getSize();
//
//        //参数值一致
//        assertEquals(20,((Cat)catZoo).getSize());
//        assertEquals(30.03,((Dog)dogZoo).getSpeed());
    }


}
