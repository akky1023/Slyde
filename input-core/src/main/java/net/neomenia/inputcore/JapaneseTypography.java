package net.neomenia.inputcore;

public record JapaneseTypography(boolean comma,boolean period,boolean openParen,boolean closeParen) {
    public String format(String input){
        return input.replace('、',comma?'，':'、').replace('。',period?'．':'。').replace('（',openParen?'(':'（').replace('）',closeParen?')':'）');
    }
}
