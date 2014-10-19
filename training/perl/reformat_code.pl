#!/usr/bin/env perl
use warnings;

my @input_files = @ARGV;

foreach my $input_file (@input_files) {
    open(my $fh, '<', $input_file) or die "Failed to load $input_file\n";
    my @lines = <$fh>;
    close $fh;
    
    my $text = join '', @lines;
    
    $text =~ s/\r?\n\s*\{/ \{/gs;
                             
    open($fh, '>', $input_file) or die "Failed to open $input_file for writing\n";
    print $fh $text;
    close $fh;
}
