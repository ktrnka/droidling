use warnings;
use LangIDTable;

my $model_dir = $ARGV[0];
my $test_dir = $ARGV[1];

opendir $model_dh, $model_dir;
my @model_files = readdir $model_dh;
closedir $model_dh;

@model_files = grep /\.lid$/, @model_files;

print "Loading models...\n";
foreach my $file (@model_files)
	{
	my $model = new LangIDTable('xx', 'xx');
	$model->load("$model_dir/$file");
	push @models, $model;
	}

opendir $test_dh, $test_dir;
my @test_files = readdir $test_dh;
closedir $test_dh;

@test_files = grep /\.txt$/, @test_files;

foreach my $file (@test_files)
	{
	my $encoding = 'ISO-8859-1';
	if ($file =~ /iso-?8859[_\-](\d)/i)
		{
		$encoding = "ISO-8859-$1";
		}
	elsif ($file =~ /(?:win(?:dows)?|cp)-?1252/i)
		{
		$encoding = "Windows-1252";
		}
	elsif ($file =~ /^sample/)
		{
		$encoding = 'UTF-8';
		}
	
	print "Testing $file ($encoding)...\n";
	open my $in, "<:encoding($encoding)", "$test_dir/$file";
	my @lines = <$in>;
	close $in;
	
	my $text = join('', @lines);
	
	my @scored_models = ();
	foreach my $model (@models)
		{
		push @scored_models, [ $model, $model->score($text) ];
		}
	
	@scored_models = sort { $b->[1] <=> $a->[1] } @scored_models;
	
	print "\tBEST MODEL: $scored_models[0]->[0]->{name} ($scored_models[0]->[0]->{code2}) = $scored_models[0]->[1]\n";
	print "\t       2nd: $scored_models[1]->[0]->{name} ($scored_models[1]->[0]->{code2}) = $scored_models[1]->[1]\n";
	print "\t       3rd: $scored_models[2]->[0]->{name} ($scored_models[2]->[0]->{code2}) = $scored_models[2]->[1]\n";
	
	printf "\tConfidence in best: %.2f\n", $scored_models[0]->[1] / ($scored_models[1]->[1] == 0 ? 1 : $scored_models[1]->[1]);
	
	my @examples = $scored_models[0]->[0]->explain_classification_words($text, $scored_models[1]->[0]);
	print "\tWhy?\n";
	print "\t\tThe text contains the following common, somewhat unique $scored_models[0]->[0]->{name} words: " . join(' ', @examples[0 .. (@examples < 4 ? @examples-1 : 4)]) . "\n";

	my @example_chars = $scored_models[0]->[0]->explain_classification_chars($text, $scored_models[1]->[0]);
	print "\t\tThe text contains the following common, somewhat unique $scored_models[0]->[0]->{name} characters: " . join(' ', @example_chars[0 .. (@example_chars < 4 ? @example_chars-1 : 4)]) . "\n";
	}